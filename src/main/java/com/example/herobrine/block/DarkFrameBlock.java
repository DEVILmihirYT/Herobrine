package com.example.herobrine.block;

import com.example.HerobrineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class DarkFrameBlock extends Block {
    public DarkFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            net.minecraft.world.level.redstone.Orientation orientation,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()) {
            DarkPortalBlock.tryActivate((ServerLevel) level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            DarkPortalBlock.tryActivate((ServerLevel) level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
