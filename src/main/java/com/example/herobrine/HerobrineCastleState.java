package com.example.herobrine;

import com.example.HerobrineMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HerobrineCastleState extends SavedData {
    private static final Codec<HerobrineCastleState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("castleX", 0).forGetter(state -> state.castleX),
                    Codec.INT.optionalFieldOf("castleY", 0).forGetter(state -> state.castleY),
                    Codec.INT.optionalFieldOf("castleZ", 0).forGetter(state -> state.castleZ),
                    Codec.BOOL.optionalFieldOf("coordinatesRevealed", false)
                            .forGetter(state -> state.coordinatesRevealed),
                    Codec.BOOL.optionalFieldOf("castleGenerated", false)
                            .forGetter(state -> state.castleGenerated)
            ).apply(instance, HerobrineCastleState::new)
    );

    public static final SavedDataType<HerobrineCastleState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, "herobrine_castle"),
            HerobrineCastleState::new,
            CODEC,
            null
    );

    private int castleX;
    private int castleY;
    private int castleZ;
    private boolean coordinatesRevealed;
    private boolean castleGenerated;

    public HerobrineCastleState() {
        this(0, 0, 0, false, false);
    }

    private HerobrineCastleState(
            int castleX,
            int castleY,
            int castleZ,
            boolean coordinatesRevealed,
            boolean castleGenerated
    ) {
        this.castleX = castleX;
        this.castleY = castleY;
        this.castleZ = castleZ;
        this.coordinatesRevealed = coordinatesRevealed;
        this.castleGenerated = castleGenerated;
    }

    public static HerobrineCastleState get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return new HerobrineCastleState();
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isCoordinatesRevealed() {
        return coordinatesRevealed;
    }

    public boolean isCastleGenerated() {
        return castleGenerated;
    }

    public BlockPos getCastlePosition() {
        return new BlockPos(castleX, castleY, castleZ);
    }

    public void revealCoordinates(BlockPos position) {
        castleX = position.getX();
        castleY = position.getY();
        castleZ = position.getZ();
        coordinatesRevealed = true;
        setDirty();
    }

    public void markCastleGenerated() {
        if (!castleGenerated) {
            castleGenerated = true;
            setDirty();
        }
    }
}
