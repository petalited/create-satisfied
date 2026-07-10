package com.createsatisfied.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Locale;

import com.createsatisfied.config.CreateSatisfiedConfig;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public class ThroughputFormat {

    public static final int TICKS_PER_MINUTE = 20 * 60;

    private ThroughputFormat() {
    }

    /**
     * Gray hint appended wherever a rate/duration number is shown, so the timeUnit config isn't
     * only discoverable by digging through the mod's config screen - names the *current* unit
     * too, since otherwise there's no way to tell which of the 3 modes is active without opening
     * the config screen or comparing numbers against memory.
     */
    public static Component scrollHintLine() {
        String unit = CreateSatisfiedConfig.TIME_UNIT.get().displayName();
        return Component.literal("(Scroll to cycle time units - " + unit + ")").withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * GoggleThroughputOverlay's own out-of-GUI copy of this hint - unlike every other caller of
     * scrollHintLine() above (all inside a GUI screen, where vanilla doesn't scroll the hotbar
     * anyway), plain scroll while goggles are on and looking at a machine would otherwise fight
     * hotbar-switching, so GoggleThroughputOverlay.onMouseScroll requires sneaking too - this hint
     * needs to say so, or the behavior looks broken instead of just gated.
     */
    public static Component scrollHintLineSneak() {
        String unit = CreateSatisfiedConfig.TIME_UNIT.get().displayName();
        return Component.literal("(Sneak + Scroll to cycle time units - " + unit + ")").withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Checks the three ways a kinetic machine can fail to actually run a recipe despite looking
     * powered, matching the design goal of distinguishing these instead of collapsing them into
     * one flat "stalled" state: overstressed (network demands more stress than its capacity -
     * {@code KineticBlockEntity.getSpeed()} silently returns 0 in this case too, so it must be
     * checked before a plain speed==0 check or it reads as a generic stall), truly stalled (no
     * power reaching it at all), and running but below the block's required {@code SpeedLevel}
     * (only Mechanical Mixer enforces this - MEDIUM minimum - but the check is generic). Returns
     * null when the machine is running normally.
     */
    public static Component kineticStateLine(KineticBlockEntity be) {
        if (be.isOverStressed()) {
            return Component.literal("Overstressed").withStyle(ChatFormatting.RED);
        }
        if (be.getSpeed() == 0) {
            return Component.literal("Stalled").withStyle(ChatFormatting.GRAY);
        }
        if (!be.isSpeedRequirementFulfilled()) {
            // isSpeedRequirementFulfilled() already confirmed the block is IRotate with a real
            // (non-NONE) requirement, or this branch wouldn't be reachable - see its own impl.
            IRotate.SpeedLevel required = ((IRotate) be.getBlockState().getBlock()).getMinimumRequiredSpeedLevel();
            String name = required.name().charAt(0) + required.name().substring(1).toLowerCase(Locale.ROOT);
            return Component.literal("Too slow to run (needs " + name + ")").withStyle(ChatFormatting.GOLD);
        }
        return null;
    }

    /**
     * Stress impact (su) for a kinetic block scales linearly with its own rotation speed -
     * {@code KineticNetwork.getActualStressOf} multiplies the block's flat configured impact
     * ({@link BlockStressValues#getImpact}) by {@code abs(speed)}. Unlike recipe throughput, this
     * has nothing to do with which recipe is loaded, so it's the same formula everywhere.
     */
    public static Component stressLine(Block block, float rpm) {
        return stressLine(block, rpm, 1);
    }

    /**
     * Same as {@link #stressLine(Block, float)}, but for machines made of multiple identical
     * kinetic blocks that each contribute their own impact (e.g. a Crushing Wheel pair).
     */
    public static Component stressLine(Block block, float rpm, int count) {
        float total = (float) BlockStressValues.getImpact(block) * Math.abs(rpm) * count;
        String suffix = count > 1 ? " (x" + count + ")" : "";
        return Component.literal(String.format("Stress: %.1f su%s", total, suffix)).withStyle(ChatFormatting.AQUA);
    }

    // TICKS_PER_ITEM only changes how a *rate* reads (inverted to "ticks needed for one item/
    // bucket" instead of "items/mB per tick") - a duration is already an absolute tick count with
    // nothing left to invert, so it behaves the same as PER_TICK in both tick-based modes.
    private static boolean isTickBased() {
        CreateSatisfiedConfig.TimeUnit unit = CreateSatisfiedConfig.TIME_UNIT.get();
        return unit == CreateSatisfiedConfig.TimeUnit.PER_TICK || unit == CreateSatisfiedConfig.TimeUnit.TICKS_PER_ITEM;
    }

    /**
     * Central item/fluid-agnostic rate formatter honoring the timeUnit config - every rate-shaped
     * line in the mod goes through this (or {@link #formatFluidRate}) rather than hardcoding
     * "/min (/s)" itself, so the config only needs to be checked in one place.
     */
    public static String formatItemRate(float perMinute) {
        if (CreateSatisfiedConfig.TIME_UNIT.get() == CreateSatisfiedConfig.TimeUnit.TICKS_PER_ITEM) {
            if (perMinute <= 0f) {
                return "never";
            }
            return String.format("%.1f ticks/item", TICKS_PER_MINUTE / perMinute);
        }
        if (isTickBased()) {
            return String.format("%.2f/tick", perMinute / TICKS_PER_MINUTE);
        }
        return String.format("%.1f/min (%.1f/s)", perMinute, perMinute / 60f);
    }

    /**
     * Unlike {@link #formatItemRate}, mB/s is always shown here alongside whichever primary unit
     * is selected (not just in PER_MINUTE mode) - fluid rates are commonly reasoned about in mB/s
     * regardless of timeUnit preference, so it stays a stable secondary reference in every mode
     * rather than disappearing entirely in the tick-based ones.
     */
    public static String formatFluidRate(float mbPerMinute) {
        float mbPerSecond = mbPerMinute / 60f;
        if (CreateSatisfiedConfig.TIME_UNIT.get() == CreateSatisfiedConfig.TimeUnit.TICKS_PER_ITEM) {
            // Ticks per single mB - mirrors formatItemRate's ticks/item exactly, and is genuinely
            // useful for slow fluid consumption (a recipe sipping a few mB over many ticks reads
            // better as "20 ticks/mB" than a tiny "0.05 mB/tick"). Without this, TICKS_PER_ITEM
            // would fall through to the exact same text as PER_TICK below (mB has no per-item
            // concept of its own), making two of the three cycle positions look like duplicates
            // for any fluid-only display (e.g. the Pump).
            if (mbPerMinute <= 0f) {
                return "never";
            }
            return String.format("%.2f ticks/mB (%.0f mB/s)", TICKS_PER_MINUTE / mbPerMinute, mbPerSecond);
        }
        if (isTickBased()) {
            return String.format("%.1f mB/tick (%.0f mB/s)", mbPerMinute / TICKS_PER_MINUTE, mbPerSecond);
        }
        return String.format("%.0f mB/min (%.0f mB/s)", mbPerMinute, mbPerSecond);
    }

    /**
     * For durations (how long one operation/batch takes), as opposed to the rates above. Use the
     * {@link #formatDuration(float, int)} overload instead whenever the duration covers a batch of
     * more than one item - a bare tick count has nothing left to invert for TICKS_PER_ITEM, so this
     * single-item form is only for durations with no batch concept at all (e.g. one full Sequenced
     * Assembly cycle, which always yields exactly one output regardless of PER_TICK/TICKS_PER_ITEM).
     */
    public static String formatDuration(float ticks) {
        if (isTickBased()) {
            return String.format("%.0f ticks", ticks);
        }
        return String.format("%.1fs", ticks / 20f);
    }

    /**
     * Same as {@link #formatDuration(float)}, but for a duration covering {@code itemCount} items
     * in one batch (Crushing/Fan-processing batches, etc.) - TICKS_PER_ITEM divides down to a
     * genuine per-single-item tick count instead of falling through to the exact same "X ticks"
     * text PER_TICK already shows for the whole batch, which read as a dead scroll step (2 of the
     * 3 cycle positions looking identical).
     */
    public static String formatDuration(float ticks, int itemCount) {
        if (CreateSatisfiedConfig.TIME_UNIT.get() == CreateSatisfiedConfig.TimeUnit.TICKS_PER_ITEM) {
            return String.format("%.1f ticks/item", ticks / itemCount);
        }
        return formatDuration(ticks);
    }

    public static Component rateLine(String prefix, String itemName, float itemsPerMinute, ChatFormatting color) {
        String text = String.format("%s %s  %s", prefix, itemName, formatItemRate(itemsPerMinute));
        return Component.literal(text).withStyle(color);
    }

    /**
     * For displays framed around one concrete batch (count + how long that batch takes) rather
     * than an extrapolated rate - shows the absolute amount for this batch plus the rate that
     * implies, e.g. "- Copper Ore  x64  32.0/min (0.5/s)". {@code amountText} is the
     * already-formatted amount (e.g. "x64" for an exact count, "~320.0" for a chance-weighted
     * expected count); {@code countForRate} is that same amount as a number, used for the rate.
     */
    public static Component batchLine(String prefix, String itemName, String amountText, float countForRate, float secondsPerBatch, ChatFormatting color) {
        float itemsPerMinute = countForRate / secondsPerBatch * 60f;
        String text = String.format("%s %s  %s  %s", prefix, itemName, amountText, formatItemRate(itemsPerMinute));
        return Component.literal(text).withStyle(color);
    }

    public static Component fluidRateLine(String prefix, String fluidName, float mbPerMinute, ChatFormatting color) {
        String text = String.format("%s %s  %s", prefix, fluidName, formatFluidRate(mbPerMinute));
        return Component.literal(text).withStyle(color);
    }

    public static Component fluidBatchLine(String prefix, String fluidName, float totalMb, float secondsPerBatch, ChatFormatting color) {
        float mbPerMinute = totalMb / secondsPerBatch * 60f;
        String text = String.format("%s %s  %.0fmB  %s", prefix, fluidName, totalMb, formatFluidRate(mbPerMinute));
        return Component.literal(text).withStyle(color);
    }

    /**
     * Builds the standard "- input ... +output ..." lines for any recipe. Uses
     * {@link ProcessingRecipe}'s chance-weighted rollable results when available (Create's own
     * recipes), falling back to a single result item for plain vanilla recipes (e.g. the vanilla
     * Smoking/Blasting recipes Create's fan_smoking/fan_blasting JEI categories reuse as-is).
     */
    public static List<Component> buildRateLines(Recipe<?> recipe, float opsPerMinute) {
        List<Component> lines = new ArrayList<>();

        if (!recipe.getIngredients().isEmpty()) {
            ItemStack[] items = recipe.getIngredients().get(0).getItems();
            String name = items.length > 0 ? items[0].getHoverName().getString() : "Input";
            lines.add(rateLine("-", name, opsPerMinute, ChatFormatting.RED));
        }

        if (recipe instanceof ProcessingRecipe<?, ?> processingRecipe) {
            for (SizedFluidIngredient fluidIngredient : processingRecipe.getFluidIngredients()) {
                FluidStack[] fluids = fluidIngredient.getFluids();
                if (fluids.length > 0) {
                    String name = fluids[0].getHoverName().getString();
                    lines.add(fluidRateLine("-", name, opsPerMinute * fluidIngredient.amount(), ChatFormatting.RED));
                }
            }

            for (Map.Entry<Item, Float> entry : combineOutputs(processingRecipe.getRollableResults(), opsPerMinute).entrySet()) {
                String name = new ItemStack(entry.getKey()).getHoverName().getString();
                lines.add(rateLine("+", name, entry.getValue(), ChatFormatting.GREEN));
            }

            for (Map.Entry<Fluid, Float> entry : combineFluids(processingRecipe.getFluidResults(), opsPerMinute).entrySet()) {
                String name = new FluidStack(entry.getKey(), 1).getHoverName().getString();
                lines.add(fluidRateLine("+", name, entry.getValue(), ChatFormatting.GREEN));
            }
        } else {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
                    lines.add(rateLine("+", result.getHoverName().getString(), opsPerMinute * result.getCount(), ChatFormatting.GREEN));
                }
            }
        }

        return lines;
    }

    /**
     * Fans discount processing time in chunks of 16 items (ceil(count/16)) - the fan-processing
     * equivalent of Crushing Wheels' batch discount. Given the config-based fan processing time
     * and a batch size, this is the ticks for one whole batch (not per-item).
     */
    public static float fanProcessingTicks(int stackSize) {
        int timeModifier = (stackSize - 1) / 16 + 1;
        return AllConfigs.server().kinetics.fanProcessingTime.get() * timeModifier + 1;
    }

    /**
     * Builds "- input ... +output ..." lines framed around one concrete batch (amount + the rate
     * that implies), for any recipe. Uses {@link ProcessingRecipe}'s chance-weighted rollable
     * results when available (Create's own recipes), falling back to a single result item for
     * plain vanilla recipes (e.g. the vanilla Smoking/Blasting recipes Create's
     * fan_smoking/fan_blasting JEI categories reuse as-is).
     */
    public static List<Component> buildBatchLines(Recipe<?> recipe, int stackSize, float secondsPerBatch) {
        List<Component> lines = new ArrayList<>();

        if (!recipe.getIngredients().isEmpty()) {
            ItemStack[] items = recipe.getIngredients().get(0).getItems();
            String name = items.length > 0 ? items[0].getHoverName().getString() : "Input";
            lines.add(batchLine("-", name, "x" + stackSize, stackSize, secondsPerBatch, ChatFormatting.RED));
        }

        if (recipe instanceof ProcessingRecipe<?, ?> processingRecipe) {
            for (SizedFluidIngredient fluidIngredient : processingRecipe.getFluidIngredients()) {
                FluidStack[] fluids = fluidIngredient.getFluids();
                if (fluids.length > 0) {
                    String name = fluids[0].getHoverName().getString();
                    lines.add(fluidBatchLine("-", name, stackSize * (float) fluidIngredient.amount(), secondsPerBatch, ChatFormatting.RED));
                }
            }

            for (Map.Entry<Item, Float> entry : combineOutputs(processingRecipe.getRollableResults(), stackSize).entrySet()) {
                String name = new ItemStack(entry.getKey()).getHoverName().getString();
                float expectedCount = entry.getValue();
                lines.add(batchLine("+", name, String.format("~%.1f", expectedCount), expectedCount, secondsPerBatch, ChatFormatting.GREEN));
            }

            for (Map.Entry<Fluid, Float> entry : combineFluids(processingRecipe.getFluidResults(), stackSize).entrySet()) {
                String name = new FluidStack(entry.getKey(), 1).getHoverName().getString();
                lines.add(fluidBatchLine("+", name, entry.getValue(), secondsPerBatch, ChatFormatting.GREEN));
            }
        } else {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
                    float expectedCount = stackSize * result.getCount();
                    lines.add(batchLine("+", result.getHoverName().getString(), String.format("~%.1f", expectedCount), expectedCount, secondsPerBatch, ChatFormatting.GREEN));
                }
            }
        }

        return lines;
    }

    /**
     * Sequenced Assembly's result pool is a single weighted random pick per completion (unlike
     * {@link ProcessingRecipe#getRollableResults()}, where each entry independently may or may not
     * fire) - so each entry's share of {@code completionsPerMinute} is its chance normalized
     * against the pool's total chance weight, not the raw chance value itself. Mirrors how
     * {@code SequencedAssemblyRecipe.getOutputChance()}/{@code rollResult()} treat the pool.
     */
    public static List<Component> buildWeightedOutputLines(List<ProcessingOutput> resultPool, float completionsPerMinute) {
        List<Component> lines = new ArrayList<>();

        // A single-entry pool always normalizes to 100% (chance/totalWeight == 1 no matter what
        // the authored chance value is) - it's not actually random, so showing a "chance" percent
        // reads as if there's a failure case when there isn't one. Matches Create's own JEI
        // category, which only shows its chance/junk UI when recipe.getOutputChance() != 1.0F.
        if (resultPool.size() == 1) {
            ItemStack stack = resultPool.get(0).getStack();
            float itemsPerMinute = completionsPerMinute * stack.getCount();
            lines.add(rateLine("+", stack.getHoverName().getString(), itemsPerMinute, ChatFormatting.GREEN));
            return lines;
        }

        float totalWeight = 0f;
        for (ProcessingOutput output : resultPool) {
            totalWeight += output.getChance();
        }
        if (totalWeight <= 0f) {
            return lines;
        }

        Map<Item, Float> probabilities = new LinkedHashMap<>();
        Map<Item, Float> ratesPerMinute = new LinkedHashMap<>();
        for (ProcessingOutput output : resultPool) {
            ItemStack stack = output.getStack();
            float probability = output.getChance() / totalWeight;
            probabilities.merge(stack.getItem(), probability, Float::sum);
            ratesPerMinute.merge(stack.getItem(), completionsPerMinute * probability * stack.getCount(), Float::sum);
        }

        for (Map.Entry<Item, Float> entry : ratesPerMinute.entrySet()) {
            Item item = entry.getKey();
            float itemsPerMinute = entry.getValue();
            float probability = probabilities.get(item);
            String pct = probability < 0.01f ? "<1" : probability > 0.99f ? ">99" : String.valueOf(Math.round(probability * 100f));
            String name = new ItemStack(item).getHoverName().getString();
            String text = String.format("+ %s  %s%% chance  ~%s", name, pct, formatItemRate(itemsPerMinute));
            lines.add(Component.literal(text).withStyle(ChatFormatting.GREEN));
        }

        return lines;
    }

    /**
     * Some recipes list the same output item as two separate rollable entries (e.g. a guaranteed
     * amount plus a separate chance-based bonus roll of the same item, common on ore crushing
     * recipes) - shown separately that reads as a duplicate/bug, so this combines entries for the
     * same item into a single total before display.
     */
    private static Map<Item, Float> combineOutputs(List<ProcessingOutput> outputs, float countMultiplier) {
        Map<Item, Float> combined = new LinkedHashMap<>();
        for (ProcessingOutput output : outputs) {
            ItemStack stack = output.getStack();
            float amount = countMultiplier * stack.getCount() * output.getChance();
            combined.merge(stack.getItem(), amount, Float::sum);
        }
        return combined;
    }

    /**
     * Same idea as {@link #combineOutputs} but for fluid results, which aren't chance-weighted
     * (always guaranteed) but can still list the same fluid more than once.
     */
    private static Map<Fluid, Float> combineFluids(List<FluidStack> results, float countMultiplier) {
        Map<Fluid, Float> combined = new LinkedHashMap<>();
        for (FluidStack stack : results) {
            float amount = countMultiplier * stack.getAmount();
            combined.merge(stack.getFluid(), amount, Float::sum);
        }
        return combined;
    }
}
