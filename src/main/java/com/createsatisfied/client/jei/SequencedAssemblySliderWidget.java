package com.createsatisfied.client.jei;

import com.createsatisfied.client.SpeedFormula;
import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.config.CreateSatisfiedConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sequenced Assembly chains Pressing/Cutting/Deploying/Filling steps together, looping the whole
 * sequence a fixed number of times before rolling a chance-weighted final item. There's no single
 * machine to read an rpm from here (it's a whole line of machines), so this assumes every step
 * runs at the same shared rpm and sums each step's own per-step time - Deploying ignores the
 * recipe but still needs rpm, Filling ignores rpm entirely (Spout isn't kinetic) - to get one
 * total-cycle-time number, matching the per-item-math rule in [[feedback-throughput-realism]].
 */
public class SequencedAssemblySliderWidget implements IRecipeWidget, IJeiInputHandler {

    public static final int EXTRA_HEIGHT = 20;
    private static final int MAX_RPM = 256;
    private static final int TRACK_MARGIN = 8;
    private static final int TRACK_Y = 6;
    private static final int TRACK_HEIGHT = 4;
    private static final int HANDLE_OUTLINE = 0xFF1A1A1A;
    private static final int HANDLE_FILL = 0xFFE0E0E0;

    private final SequencedAssemblyRecipe recipe;
    private final int baseHeight;
    private final int width;
    private float rpm = 16f;

    public SequencedAssemblySliderWidget(SequencedAssemblyRecipe recipe, int baseHeight, int width) {
        this.recipe = recipe;
        this.baseHeight = baseHeight;
        this.width = width;
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(0, baseHeight);
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        int trackLeft = TRACK_MARGIN;
        int trackRight = width - TRACK_MARGIN;
        guiGraphics.fill(trackLeft, TRACK_Y, trackRight, TRACK_Y + TRACK_HEIGHT, 0xFF3A3A3A);

        float t = Mth.clamp(rpm / MAX_RPM, 0f, 1f);
        int handleX = trackLeft + Math.round(t * (trackRight - trackLeft));
        guiGraphics.fill(handleX - 2, TRACK_Y - 2, handleX + 2, TRACK_Y + TRACK_HEIGHT + 2, HANDLE_OUTLINE);
        guiGraphics.fill(handleX - 1, TRACK_Y - 1, handleX + 1, TRACK_Y + TRACK_HEIGHT + 1, HANDLE_FILL);

        Component label = Component.literal(Math.round(rpm) + " RPM (all steps)");
        guiGraphics.drawString(Minecraft.getInstance().font, label, trackLeft, TRACK_Y + 6, 0xFF404040, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        float totalTicks = 0f;
        for (SequencedRecipe<?> step : recipe.getSequence()) {
            float opsPerMinute = resolveOpsPerMinute(step.getRecipe(), rpm);
            if (opsPerMinute <= 0f) {
                tooltip.add(Component.literal("Stalled").withStyle(ChatFormatting.GRAY));
                tooltip.add(ThroughputFormat.scrollHintLine());
                return;
            }
            totalTicks += ThroughputFormat.TICKS_PER_MINUTE / opsPerMinute;
        }
        totalTicks *= recipe.getLoops();

        float completionsPerMinute = ThroughputFormat.TICKS_PER_MINUTE / totalTicks;

        String header = String.format("Full sequence: %s (x%d loops)", ThroughputFormat.formatDuration(totalTicks), recipe.getLoops());
        tooltip.add(Component.literal(header).withStyle(ChatFormatting.WHITE));

        ItemStack[] ingredientItems = recipe.getIngredient().getItems();
        if (ingredientItems.length > 0) {
            tooltip.add(ThroughputFormat.rateLine("-", ingredientItems[0].getHoverName().getString(), completionsPerMinute, ChatFormatting.RED));
        }

        // recipe.getIngredient() only tracks the one main item progressing through the sequence -
        // Deploying steps apply a *second* item (e.g. nuggets for Track) that never shows up
        // there, since initFromSequencedAssembly only rewrites ingredient slot 0 to the
        // transitional item, leaving slot 1 (the applied item) as originally authored. Each loop
        // runs every step exactly once, so an applied item costs one per loop unless the step is
        // flagged to not consume it (shouldKeepHeldItem, e.g. tools). Filling steps similarly
        // consume a fluid that's otherwise invisible here.
        Map<Item, Float> extraItemRates = new LinkedHashMap<>();
        Map<Fluid, Float> extraFluidRates = new LinkedHashMap<>();
        for (SequencedRecipe<?> step : recipe.getSequence()) {
            ProcessingRecipe<?, ?> stepRecipe = step.getRecipe();
            if (stepRecipe instanceof ItemApplicationRecipe appRecipe && !appRecipe.shouldKeepHeldItem()) {
                ItemStack[] appliedItems = appRecipe.getRequiredHeldItem().getItems();
                if (appliedItems.length > 0) {
                    extraItemRates.merge(appliedItems[0].getItem(), completionsPerMinute, Float::sum);
                }
            }
            for (SizedFluidIngredient fluidIngredient : stepRecipe.getFluidIngredients()) {
                FluidStack[] fluids = fluidIngredient.getFluids();
                if (fluids.length > 0) {
                    extraFluidRates.merge(fluids[0].getFluid(), completionsPerMinute * fluidIngredient.amount(), Float::sum);
                }
            }
        }
        for (Map.Entry<Item, Float> entry : extraItemRates.entrySet()) {
            String name = new ItemStack(entry.getKey()).getHoverName().getString();
            tooltip.add(ThroughputFormat.rateLine("-", name, entry.getValue(), ChatFormatting.RED));
        }
        for (Map.Entry<Fluid, Float> entry : extraFluidRates.entrySet()) {
            String name = new FluidStack(entry.getKey(), 1).getHoverName().getString();
            tooltip.add(ThroughputFormat.fluidRateLine("-", name, entry.getValue(), ChatFormatting.RED));
        }

        tooltip.addAll(ThroughputFormat.buildWeightedOutputLines(recipe.resultPool, completionsPerMinute));

        // Every physical machine in the line spins continuously on the shared network regardless
        // of which step is currently active, so the real total is the sum of every step's own
        // block impact - not just whichever step the item happens to be visiting right now.
        // Filling/Spout isn't kinetic at all, so it contributes nothing here. Shown broken down
        // per machine type (with count) rather than one opaque total - a flat "4096 su" reads as
        // one machine's cost when it's actually e.g. 2 Deployers + 1 Press combined, and those
        // per-block numbers don't obviously add up to a round total at a glance.
        Map<Block, Integer> machineCounts = new LinkedHashMap<>();
        for (SequencedRecipe<?> step : recipe.getSequence()) {
            Block block = resolveStressBlock(step.getRecipe());
            if (block != null) {
                machineCounts.merge(block, 1, Integer::sum);
            }
        }
        float totalStress = 0f;
        for (Map.Entry<Block, Integer> entry : machineCounts.entrySet()) {
            float stress = (float) BlockStressValues.getImpact(entry.getKey()) * Math.abs(rpm) * entry.getValue();
            totalStress += stress;
            String name = new ItemStack(entry.getKey()).getHoverName().getString();
            String countSuffix = entry.getValue() > 1 ? " x" + entry.getValue() : "";
            tooltip.add(Component.literal(String.format("- %s%s: %.1f su", name, countSuffix, stress)).withStyle(ChatFormatting.AQUA));
        }
        // Distinct from the per-machine AQUA lines above (Create's own color for a stress number)
        // so the combined total doesn't read as just another machine's individual cost - GOLD was
        // avoided since Create already uses it for the "Overstressed" warning elsewhere.
        tooltip.add(Component.literal(String.format("Total stress: %.1f su", totalStress)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(ThroughputFormat.scrollHintLine());
    }

    private static float resolveOpsPerMinute(ProcessingRecipe<?, ?> stepRecipe, float rpm) {
        if (stepRecipe instanceof PressingRecipe) {
            return SpeedFormula.press().opsPerMinute(rpm, stepRecipe);
        }
        if (stepRecipe instanceof CuttingRecipe) {
            return SpeedFormula.continuous(24f, 1f, 128f).opsPerMinute(rpm, stepRecipe);
        }
        if (stepRecipe instanceof ItemApplicationRecipe) {
            return SpeedFormula.deployer().opsPerMinute(rpm, stepRecipe);
        }
        if (stepRecipe instanceof FillingRecipe) {
            return ThroughputFormat.TICKS_PER_MINUTE / (float) SpoutBlockEntity.FILLING_TIME;
        }
        return 0f;
    }

    private static Block resolveStressBlock(ProcessingRecipe<?, ?> stepRecipe) {
        if (stepRecipe instanceof PressingRecipe) {
            return AllBlocks.MECHANICAL_PRESS.get();
        }
        if (stepRecipe instanceof CuttingRecipe) {
            return AllBlocks.MECHANICAL_SAW.get();
        }
        if (stepRecipe instanceof ItemApplicationRecipe) {
            return AllBlocks.DEPLOYER.get();
        }
        return null;
    }

    @Override
    public ScreenRectangle getArea() {
        return new ScreenRectangle(0, baseHeight, width, EXTRA_HEIGHT);
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        if (input.isSimulate()) {
            return true;
        }
        updateRpm(mouseX);
        return true;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        updateRpm(mouseX);
        return true;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (scrollDeltaY == 0) {
            return false;
        }
        CreateSatisfiedConfig.cycleTimeUnit(scrollDeltaY > 0 ? 1 : -1);
        return true;
    }

    private void updateRpm(double mouseX) {
        float t = Mth.clamp((float) ((mouseX - TRACK_MARGIN) / (width - 2 * TRACK_MARGIN)), 0f, 1f);
        rpm = Math.round(t * MAX_RPM);
    }
}
