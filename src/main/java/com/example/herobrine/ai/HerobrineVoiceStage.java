package com.example.herobrine.ai;

import com.example.herobrine.HerobrineStage;

public enum HerobrineVoiceStage {
    HARRY("SOYHLrjzK2X1ezoPC6cr"),
    CHARLIE("IKne3meq5aSn9XLyUdCD"),
    ADAM("pNInz6obpgDQGcFmaJgB");

    private final String voiceId;

    HerobrineVoiceStage(String voiceId) {
        this.voiceId = voiceId;
    }

    public String voiceId() {
        return voiceId;
    }

    public static HerobrineVoiceStage forStage(HerobrineStage stage) {
        return switch (stage) {
            case STAGE_1 -> HARRY;
            case STAGE_2 -> CHARLIE;
            case STAGE_3 -> ADAM;
        };
    }
}
