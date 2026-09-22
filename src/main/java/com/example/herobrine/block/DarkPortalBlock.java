package com.example.herobrine.block;

import com.example.herobrine.HerobrineStage;
import com.example.herobrine.HerobrineWorldState;
import com.example.herobrine.entity.HerobrineEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class DarkPortalBlock extends Block {
    private static final int WIDTH = 2;
    private static final int HEIGHT = 3;

    public DarkPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static void tryActivate(ServerLevel level, BlockPos frameHint) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            for (int ox = -3; ox <= 0; ox++) {
                for (int oy = -4; oy <= 0; oy++) {
                    BlockPos origin = axis == Direction.Axis.X
                            ? frameHint.offset(ox, oy, 0)
                            : frameHint.offset(0, oy, ox);
                    if (isCompleteFrame(level, origin, axis)) {
                        fillPortal(level, origin, axis);
                        return;
                    }
                }
            }
        }
    }

    private static boolean isCompleteFrame(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        for (int y = 0; y < HEIGHT + 2; y++) {
            for (int w = 0; w < WIDTH + 2; w++) {
                boolean frame = y == 0 || y == HEIGHT + 1 || w == 0 || w == WIDTH + 1;
                BlockPos pos = axis == Direction.Axis.X
                        ? origin.offset(w, y, 0)
                        : origin.offset(0, y, w);
                if (frame != (level.getBlockState(pos).getBlock() == ModBlocks.DARK_FRAME)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void fillPortal(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        for (int y = 1; y <= HEIGHT; y++) {
            for (int w = 1; w <= WIDTH; w++) {
                BlockPos pos = axis == Direction.Axis.X
                        ? origin.offset(w, y, 0)
                        : origin.offset(0, y, w);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, ModBlocks.DARK_PORTAL.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    public static List<BlockPos> collectPortalBlocks(ServerLevel level, BlockPos center) {
        List<BlockPos> result = new ArrayList<>();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).getBlock() == ModBlocks.DARK_PORTAL) {
                        result.add(pos.immutable());
                    }
                }
            }
        }

        return result;
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean isPrecise
    ) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (entity instanceof HerobrineEntity hero
                && hero.getStage() == HerobrineStage.STAGE_3
                && !HerobrineWorldState.get(serverLevel.getServer()).isPermanentlyDefeated()) {
            HerobrineDoomsdayManager.begin(
                    serverLevel.getServer(),
                    hero,
                    serverLevel,
                    pos
            );
        }
    }
}
