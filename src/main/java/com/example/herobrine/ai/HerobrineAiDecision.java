package com.example.herobrine.ai;

import com.example.herobrine.HerobrineAction;
import java.util.UUID;

public record HerobrineAiDecision(
        HerobrineAction action,
        String speech,
        UUID targetPlayer,
        String itemId,
        int itemCount
) {
    public static HerobrineAiDecision speak(String speech) {
        return new HerobrineAiDecision(HerobrineAction.SPEAK, speech, null, null, 0);
    }

    public static HerobrineAiDecision doNothing() {
        return new HerobrineAiDecision(HerobrineAction.OBSERVE, null, null, null, 0);
    }
}
