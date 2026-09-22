package com.example.herobrine.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public final class HerobrineRenderState extends HumanoidRenderState {
    private int stage = 1;

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }
}
