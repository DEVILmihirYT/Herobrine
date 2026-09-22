package com.example.herobrine.client;

import com.example.herobrine.HerobrineStage;
import com.example.herobrine.entity.HerobrineEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

public final class HerobrineRenderer
        extends HumanoidMobRenderer<HerobrineEntity, HerobrineRenderState, HumanoidModel<HerobrineRenderState>> {
    private static final Identifier STAGE_1_TEXTURE =
            Identifier.fromNamespaceAndPath("herobrineai", "textures/entity/herobrine_stage1.png");
    private static final Identifier STAGE_2_TEXTURE =
            Identifier.fromNamespaceAndPath("herobrineai", "textures/entity/herobrine_stage2.png");
    private static final Identifier STAGE_3_TEXTURE =
            Identifier.fromNamespaceAndPath("herobrineai", "textures/entity/herobrine_stage3.png");

    public HerobrineRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public HerobrineRenderState createRenderState() {
        return new HerobrineRenderState();
    }

    @Override
    public void extractRenderState(
            HerobrineEntity entity,
            HerobrineRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.setStage(entity.getStage() == HerobrineStage.STAGE_1
                ? 1
                : entity.getStage() == HerobrineStage.STAGE_2 ? 2 : 3);
    }

    @Override
    public Identifier getTextureLocation(HerobrineRenderState state) {
        return switch (state.getStage()) {
            case 2 -> STAGE_2_TEXTURE;
            case 3 -> STAGE_3_TEXTURE;
            default -> STAGE_1_TEXTURE;
        };
    }
}
