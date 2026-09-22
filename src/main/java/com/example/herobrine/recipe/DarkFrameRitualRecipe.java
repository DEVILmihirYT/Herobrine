package com.example.herobrine.recipe;

import com.example.herobrine.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class DarkFrameRitualRecipe extends CustomRecipe {
    private static final int DRAGON_HEADS = 3;
    private static final int NETHER_STARS = 20;
    private static final int WARDEN_SCULKS = 10;
    private static final int RESULT_COUNT = 30;

    public static final DarkFrameRitualRecipe INSTANCE = new DarkFrameRitualRecipe();
    public static final MapCodec<DarkFrameRitualRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, DarkFrameRitualRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<DarkFrameRitualRecipe> SERIALIZER =
            new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private DarkFrameRitualRecipe() {
        super(CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Counts counts = count(input);
        return counts.otherItems == 0
                && counts.dragonHeads >= DRAGON_HEADS
                && counts.netherStars >= NETHER_STARS
                && counts.wardenSculks >= WARDEN_SCULKS;
    }

    @Override
    public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return new ItemStack(ModBlocks.DARK_FRAME, RESULT_COUNT);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        int headsLeft = DRAGON_HEADS;
        int starsLeft = NETHER_STARS;
        int sculksLeft = WARDEN_SCULKS;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            int consume = 0;
            if (stack.is(Items.DRAGON_HEAD) && headsLeft > 0) {
                consume = Math.min(headsLeft, stack.getCount());
                headsLeft -= consume;
            } else if (stack.is(Items.NETHER_STAR) && starsLeft > 0) {
                consume = Math.min(starsLeft, stack.getCount());
                starsLeft -= consume;
            } else if (stack.is(net.minecraft.world.item.Items.SCULK)) {
                // Only the Warden Sculk block is a valid ingredient.
                // Keep non-required excess in the grid.
                if (sculksLeft > 0) {
                    consume = Math.min(sculksLeft, stack.getCount());
                    sculksLeft -= consume;
                }
            }

            int finalCount = stack.getCount() - consume;
            if (finalCount > 0) {
                remaining.set(slot, stack.copyWithCount(finalCount));
            }
        }

        return remaining;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static Counts count(CraftingInput input) {
        int dragonHeads = 0;
        int netherStars = 0;
        int wardenSculks = 0;
        int otherItems = 0;

        for (ItemStack stack : input.items()) {
            if (stack.is(Items.DRAGON_HEAD)) {
                dragonHeads += stack.getCount();
            } else if (stack.is(Items.NETHER_STAR)) {
                netherStars += stack.getCount();
            } else if (stack.is(Items.SCULK)) {
                wardenSculks += stack.getCount();
            } else if (!stack.isEmpty()) {
                otherItems += stack.getCount();
            }
        }

        return new Counts(dragonHeads, netherStars, wardenSculks, otherItems);
    }

    private record Counts(int dragonHeads, int netherStars, int wardenSculks, int otherItems) {
    }
}
