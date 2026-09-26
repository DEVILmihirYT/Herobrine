package com.example.herobrine;

import com.example.HerobrineMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registries;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HerobrineCastleManager {
    private static final int MIN_DISTANCE = 10000;
    private static final int MAX_DISTANCE = 12000;
    private static final int MAX_CASTLE_BLOCKS = 5_000_000;
    private static final Identifier CASTLE_SCHEMATIC = Identifier.fromNamespaceAndPath(
            HerobrineMod.MOD_ID,
            "schematics/herobrine_castle.schem"
    );

    private HerobrineCastleManager() {
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof EnderDragon)
                    || !(source.getEntity() instanceof ServerPlayer killer)
                    || !(entity.level() instanceof ServerLevel endLevel)
                    || endLevel.dimension() != Level.END) {
                return;
            }

            revealCastleCoordinates(endLevel.getServer(), killer);
        });
        HerobrineMod.LOGGER.info(
                "Herobrine Castle .schem trigger initialized: {}-{} blocks from world spawn",
                MIN_DISTANCE,
                MAX_DISTANCE
        );
    }

    public static void revealCastleCoordinates(MinecraftServer server, ServerPlayer killer) {
        HerobrineCastleState state = HerobrineCastleState.get(server);
        if (state.isCoordinatesRevealed()) {
            sendCoordinates(killer, state.getCastlePosition());
            return;
        }

        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return;
        }

        BlockPos spawn = overworld.getRespawnData().pos();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
        double distance = random.nextDouble(MIN_DISTANCE, MAX_DISTANCE + 1.0D);

        int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
        int y = overworld.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                x,
                z
        );

        BlockPos castle = new BlockPos(x, y, z);
        state.revealCoordinates(castle);

        if (!state.isCastleGenerated()) {
            if (!placeCastle(server, overworld, castle)) {
                HerobrineMod.LOGGER.error("Could not load the bundled Herobrine Castle .schem");
                return;
            }
            state.markCastleGenerated();
        }

        sendCoordinates(killer, castle);

        HerobrineMod.LOGGER.info(
                "Herobrine Castle generated at {}, {}, {} from bundled .schem",
                castle.getX(),
                castle.getY(),
                castle.getZ()
        );
    }

    /**
     * Loads Sponge Schematic Specification v2 (.schem) directly.
     * The mod intentionally does not use Minecraft's .nbt StructureTemplate loader.
     */
    private static boolean placeCastle(
            MinecraftServer server,
            ServerLevel overworld,
            BlockPos origin
    ) {
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource =
                    server.getResourceManager().getResource(CASTLE_SCHEMATIC);
            if (resource.isEmpty()) {
                HerobrineMod.LOGGER.error("Missing bundled .schem resource: {}", CASTLE_SCHEMATIC);
                return false;
            }

            NbtCompound root;
            try (InputStream input = resource.get().open()) {
                root = NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes());
            }

            int width = root.getInt("Width", 0);
            int height = root.getInt("Height", 0);
            int length = root.getInt("Length", 0);
            long volume = (long) width * height * length;

            if (width <= 0 || height <= 0 || length <= 0 || volume > MAX_CASTLE_BLOCKS) {
                HerobrineMod.LOGGER.error(
                        "Invalid/unsafe .schem dimensions: {}x{}x{} (volume {})",
                        width, height, length, volume
                );
                return false;
            }

            NbtCompound palette = root.getCompoundOrEmpty("Palette");
            byte[] blockData = root.getByteArray("BlockData").orElse(new byte[0]);
            if (palette.isEmpty() || blockData.length == 0) {
                HerobrineMod.LOGGER.error("Herobrine Castle .schem has no Palette or BlockData");
                return false;
            }

            Map<Integer, BlockState> states = parsePalette(overworld, palette);
            if (states.isEmpty()) {
                return false;
            }

            int expectedBlocks = Math.toIntExact(volume);
            int dataIndex = 0;
            int placed = 0;

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        VarIntResult decoded = readVarInt(blockData, dataIndex);
                        dataIndex = decoded.nextIndex();
                        BlockState state = states.get(decoded.value());
                        if (state == null) {
                            HerobrineMod.LOGGER.error(
                                    "Unknown palette index {} at schematic block {},{},{}",
                                    decoded.value(), x, y, z
                            );
                            return false;
                        }

                        if (!state.isAir()) {
                            overworld.setBlock(
                                    origin.offset(x, y, z),
                                    state,
                                    Block.UPDATE_ALL,
                                    512
                            );
                            placed++;
                        }
                    }
                }
            }

            if (dataIndex > blockData.length || placed > expectedBlocks) {
                return false;
            }

            restoreBlockEntities(server, overworld, origin, root.getListOrEmpty("BlockEntities"));

            HerobrineMod.LOGGER.info(
                    "Loaded Herobrine Castle .schem: {}x{}x{}; placed {} non-air blocks",
                    width, height, length, placed
            );
            return true;
        } catch (IOException | RuntimeException exception) {
            HerobrineMod.LOGGER.error("Failed to load Herobrine Castle .schem", exception);
            return false;
        }
    }

    private static Map<Integer, BlockState> parsePalette(
            ServerLevel level,
            NbtCompound palette
    ) {
        Map<Integer, BlockState> states = new HashMap<>();
        var blockLookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);

        for (String stateId : palette.getKeys()) {
            int paletteIndex = palette.getInt(stateId, -1);
            if (paletteIndex < 0) {
                continue;
            }

            try {
                BlockStateParser.BlockResult result =
                        BlockStateParser.parseForBlock(blockLookup, stateId, false);
                states.put(paletteIndex, result.state());
            } catch (CommandSyntaxException exception) {
                HerobrineMod.LOGGER.warn(
                        "Could not parse schematic block state '{}'",
                        stateId,
                        exception
                );
            }
        }

        return states;
    }

    private static void restoreBlockEntities(
            MinecraftServer server,
            ServerLevel level,
            BlockPos origin,
            NbtList entities
    ) {
        for (int i = 0; i < entities.size(); i++) {
            NbtCompound tag = entities.getCompoundOrEmpty(i);
            int[] pos = tag.getIntArray("Pos").orElse(new int[0]);
            if (pos.length < 3) {
                continue;
            }

            BlockPos worldPos = origin.offset(pos[0], pos[1], pos[2]);
            BlockEntity blockEntity = BlockEntity.loadStatic(
                    worldPos,
                    level.getBlockState(worldPos),
                    tag,
                    server.registryAccess()
            );
            if (blockEntity != null) {
                level.setBlockEntity(blockEntity);
            }
        }
    }

    private record VarIntResult(int value, int nextIndex) {
    }

    private static VarIntResult readVarInt(byte[] data, int startIndex) {
        int value = 0;
        int shift = 0;
        int index = startIndex;

        while (index < data.length && shift < 35) {
            int current = data[index++] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return new VarIntResult(value, index);
            }
            shift += 7;
        }

        throw new IllegalArgumentException("Invalid Sponge .schem VarInt block data");
    }

    private static void sendCoordinates(ServerPlayer player, BlockPos castle) {
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("CASTLE: " + castle.getX() + " " + castle.getZ())
        ));
        player.sendSystemMessage(Component.literal(
                "Herobrine Castle coordinates: X=" + castle.getX()
                        + " Y=" + castle.getY() + " Z=" + castle.getZ()
        ));
        player.sendSystemMessage(Component.literal(
                "Find the castle thousands of blocks from world spawn."
        ));
    }
}
