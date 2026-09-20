package com.example.herobrine.ai;

import com.example.herobrine.HerobrineChatMemory;
import com.example.herobrine.HerobrinePlayerTracker;
import com.example.herobrine.HerobrineStage;
import java.util.List;
import java.util.UUID;

public record HerobrineAiRequest(
        UUID playerId,
        String playerName,
        HerobrineStage stage,
        String trigger,
        List<HerobrineChatMemory.Message> recentChat,
        List<HerobrinePlayerTracker.Snapshot> trackedPlayers
) {
    public HerobrineAiRequest {
        recentChat = List.copyOf(recentChat);
        trackedPlayers = List.copyOf(trackedPlayers);
    }
}
