package com.example.herobrine;

import com.example.HerobrineMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HerobrineWorldState extends SavedData {
    public record PlayerState(
            String uuid,
            boolean grudgeActive,
            long revengeUntilDay,
            List<String> memories,
            int relationship,
            long lastInteractionDay,
            boolean goldenApplesGiven
    ) {
        private static final Codec<PlayerState> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("uuid").forGetter(PlayerState::uuid),
                        Codec.BOOL.fieldOf("grudgeActive").forGetter(PlayerState::grudgeActive),
                        Codec.LONG.fieldOf("revengeUntilDay").forGetter(PlayerState::revengeUntilDay),
                        Codec.STRING.listOf().optionalFieldOf("memories", List.of()).forGetter(PlayerState::memories),
                        Codec.INT.optionalFieldOf("relationship", 0).forGetter(PlayerState::relationship),
                        Codec.LONG.optionalFieldOf("lastInteractionDay", -1L).forGetter(PlayerState::lastInteractionDay),
                        Codec.BOOL.optionalFieldOf("goldenApplesGiven", false).forGetter(PlayerState::goldenApplesGiven)
                ).apply(instance, PlayerState::new)
        );
    }

    private static final Codec<HerobrineWorldState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.optionalFieldOf("lifecycleStartDay", -1L)
                            .forGetter(HerobrineWorldState::getLifecycleStartDay),
                    Codec.LONG.optionalFieldOf("stage2Day", -1L)
                            .forGetter(HerobrineWorldState::getStage2Day),
                    Codec.BOOL.optionalFieldOf("stage2Unlocked", false)
                            .forGetter(HerobrineWorldState::isStage2Unlocked),
                    Codec.BOOL.optionalFieldOf("permanentlyDefeated", false)
                            .forGetter(HerobrineWorldState::isPermanentlyDefeated),
                    Codec.BOOL.optionalFieldOf("starterKitGiven", false)
                            .forGetter(HerobrineWorldState::isStarterKitGiven),
                    PlayerState.CODEC.listOf().optionalFieldOf("players", List.of())
                            .forGetter(HerobrineWorldState::getPlayers)
            ).apply(instance, HerobrineWorldState::new)
    );

    public static final SavedDataType<HerobrineWorldState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HerobrineMod.MOD_ID, "herobrine_world"),
            HerobrineWorldState::new,
            CODEC,
            null
    );

    private long lifecycleStartDay;
    private long stage2Day;
    private boolean stage2Unlocked;
    private boolean permanentlyDefeated;
    private boolean starterKitGiven;
    private final List<PlayerState> players;

    public HerobrineWorldState() {
        this(-1L, -1L, false, false, false, List.of());
    }

    private HerobrineWorldState(
            long lifecycleStartDay,
            long stage2Day,
            boolean stage2Unlocked,
            boolean permanentlyDefeated,
            boolean starterKitGiven,
            List<PlayerState> players
    ) {
        this.lifecycleStartDay = lifecycleStartDay;
        this.stage2Day = stage2Day;
        this.stage2Unlocked = stage2Unlocked;
        this.permanentlyDefeated = permanentlyDefeated;
        this.starterKitGiven = starterKitGiven;
        this.players = new ArrayList<>(players);
    }

    public static HerobrineWorldState get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return new HerobrineWorldState();
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public long getLifecycleStartDay() {
        return lifecycleStartDay;
    }

    public long getStage2Day() {
        return stage2Day;
    }

    public boolean isStage2Unlocked() {
        return stage2Unlocked;
    }

    public boolean isPermanentlyDefeated() {
        return permanentlyDefeated;
    }

    public List<PlayerState> getPlayers() {
        return List.copyOf(players);
    }

    public void initializeLifecycle(long currentDay, RandomSource random) {
        if (lifecycleStartDay >= 0L) {
            return;
        }

        lifecycleStartDay = currentDay;
        stage2Day = currentDay + 10L + random.nextInt(6);
        setDirty();
    }

    public void unlockStage2() {
        if (stage2Unlocked) {
            return;
        }

        stage2Unlocked = true;
        setDirty();
    }

    public void markPermanentlyDefeated() {
        if (permanentlyDefeated) {
            return;
        }

        permanentlyDefeated = true;
        setDirty();
    }


    public boolean isStarterKitGiven() {
        return starterKitGiven;
    }

    public void markStarterKitGiven() {
        if (starterKitGiven) {
            return;
        }

        starterKitGiven = true;
        setDirty();
    }

    public boolean isGrudgeActive(UUID playerId) {
        PlayerState state = findPlayer(playerId);
        return state != null && state.grudgeActive();
    }

    public void setGrudgeActive(UUID playerId, boolean active) {
        PlayerState state = findPlayer(playerId);
        long revengeUntilDay = state == null ? -1L : state.revengeUntilDay();
        upsertPlayer(new PlayerState(
                playerId.toString(),
                active,
                revengeUntilDay,
                state == null ? List.of() : state.memories(),
                state == null ? 0 : state.relationship(),
                state == null ? -1L : state.lastInteractionDay(),
                state != null && state.goldenApplesGiven()
        ));
    }

    public long getRevengeUntilDay(UUID playerId) {
        PlayerState state = findPlayer(playerId);
        return state == null ? -1L : state.revengeUntilDay();
    }

    public void setRevengeUntilDay(UUID playerId, long day) {
        PlayerState existing = findPlayer(playerId);
        boolean grudgeActive = existing != null && existing.grudgeActive();
        upsertPlayer(new PlayerState(
                playerId.toString(),
                grudgeActive,
                day,
                existing == null ? List.of() : existing.memories(),
                existing == null ? 0 : existing.relationship(),
                existing == null ? -1L : existing.lastInteractionDay(),
                existing != null && existing.goldenApplesGiven()
        ));
    }

    public List<String> getAiMemories(UUID playerId) {
        PlayerState state = findPlayer(playerId);
        return state == null ? List.of() : state.memories();
    }

    public int getRelationship(UUID playerId) {
        PlayerState state = findPlayer(playerId);
        return state == null ? 0 : state.relationship();
    }

    public void recordAiMemory(UUID playerId, String memory, int relationshipDelta, long currentDay) {
        if (memory == null || memory.isBlank()) {
            return;
        }

        PlayerState existing = findPlayer(playerId);
        List<String> memories = new ArrayList<>(existing == null ? List.of() : existing.memories());
        String compact = memory.trim();
        if (compact.length() > 240) {
            compact = compact.substring(0, 240);
        }
        memories.add(compact);
        while (memories.size() > 8) {
            memories.remove(0);
        }

        int oldRelationship = existing == null ? 0 : existing.relationship();
        int nextRelationship = Math.max(-100, Math.min(100, oldRelationship + relationshipDelta));
        boolean grudgeActive = existing != null && existing.grudgeActive();
        long revengeUntilDay = existing == null ? -1L : existing.revengeUntilDay();

        upsertPlayer(new PlayerState(
                playerId.toString(),
                grudgeActive,
                revengeUntilDay,
                memories,
                nextRelationship,
                currentDay,
                existing != null && existing.goldenApplesGiven()
        ));
    }

    public boolean claimGoldenApples(UUID playerId) {
        PlayerState existing = findPlayer(playerId);
        if (existing != null && existing.goldenApplesGiven()) {
            return false;
        }

        upsertPlayer(new PlayerState(
                playerId.toString(),
                existing != null && existing.grudgeActive(),
                existing == null ? -1L : existing.revengeUntilDay(),
                existing == null ? List.of() : existing.memories(),
                existing == null ? 0 : existing.relationship(),
                existing == null ? -1L : existing.lastInteractionDay(),
                true
        ));
        return true;
    }

    public void clearGrudge(UUID playerId) {
        PlayerState existing = findPlayer(playerId);
        if (existing == null || !existing.grudgeActive()) {
            return;
        }

        upsertPlayer(new PlayerState(
                existing.uuid(),
                false,
                -1L,
                existing.memories(),
                existing.relationship(),
                existing.lastInteractionDay(),
                existing.goldenApplesGiven()
        ));
    }

    public void clearPlayerState(UUID playerId) {
        String id = playerId.toString();
        if (players.removeIf(state -> state.uuid().equals(id))) {
            setDirty();
        }
    }

    private PlayerState findPlayer(UUID playerId) {
        String id = playerId.toString();
        for (PlayerState state : players) {
            if (state.uuid().equals(id)) {
                return state;
            }
        }
        return null;
    }

    private void upsertPlayer(PlayerState replacement) {
        String id = replacement.uuid();

        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).uuid().equals(id)) {
                players.set(i, replacement);
                setDirty();
                return;
            }
        }

        players.add(replacement);
        setDirty();
    }
}
