package com.createsatisfied.client.jei;

import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.config.CreateSatisfiedConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;

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
import net.minecraft.world.item.crafting.Recipe;

/**
 * Fan processing (Washing/Smoking/Blasting/Haunting) doesn't depend on fan rpm at all - only on
 * whether the fan is on. What it DOES depend on is batch size, discounted in chunks of 16 items
 * (see {@link ThroughputFormat#fanProcessingTicks}), so this exposes a stack-size slider instead
 * of an rpm one - dragging rpm would visibly do nothing, dragging stack size actually changes
 * the batch time.
 */
public class FanStackSliderWidget implements IRecipeWidget, IJeiInputHandler {

    public static final int EXTRA_HEIGHT = 20;
    private static final int MAX_STACK = 64;
    private static final int TRACK_MARGIN = 8;
    private static final int TRACK_Y = 6;
    private static final int TRACK_HEIGHT = 4;
    private static final int HANDLE_OUTLINE = 0xFF1A1A1A;
    private static final int HANDLE_FILL = 0xFFE0E0E0;

    private final Recipe<?> recipe;
    private final int baseHeight;
    private final int width;
    private int stackSize = 1;

    public FanStackSliderWidget(Recipe<?> recipe, int baseHeight, int width) {
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

        float t = (stackSize - 1) / (float) (MAX_STACK - 1);
        int handleX = trackLeft + Math.round(Mth.clamp(t, 0f, 1f) * (trackRight - trackLeft));
        guiGraphics.fill(handleX - 2, TRACK_Y - 2, handleX + 2, TRACK_Y + TRACK_HEIGHT + 2, HANDLE_OUTLINE);
        guiGraphics.fill(handleX - 1, TRACK_Y - 1, handleX + 1, TRACK_Y + TRACK_HEIGHT + 1, HANDLE_FILL);

        Component label = Component.literal(stackSize + (stackSize == 1 ? " item" : " items"));
        guiGraphics.drawString(Minecraft.getInstance().font, label, trackLeft, TRACK_Y + 6, 0xFF404040, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        float ticksPerBatch = ThroughputFormat.fanProcessingTicks(stackSize);
        float secondsPerBatch = ticksPerBatch / 20f;

        String header = String.format("Batch: %s for %d item%s", ThroughputFormat.formatDuration(ticksPerBatch), stackSize, stackSize == 1 ? "" : "s");
        tooltip.add(Component.literal(header).withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildBatchLines(recipe, stackSize, secondsPerBatch));
        // Fans don't have an rpm control here (rpm is irrelevant to fan timing), but stress cost
        // still scales with whatever rpm the fan actually spins at - shown at max rpm as a
        // reference point, matching the "1-16 items / 64 items" dual-reference style above.
        float stressAt256 = (float) BlockStressValues.getImpact(AllBlocks.ENCASED_FAN.get()) * 256f;
        tooltip.add(Component.literal(String.format("Stress: %.1f su (at 256 rpm)", stressAt256)).withStyle(ChatFormatting.AQUA));
        tooltip.add(ThroughputFormat.scrollHintLine());
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
        updateStackSize(mouseX);
        return true;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        updateStackSize(mouseX);
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

    private void updateStackSize(double mouseX) {
        float t = Mth.clamp((float) ((mouseX - TRACK_MARGIN) / (width - 2 * TRACK_MARGIN)), 0f, 1f);
        stackSize = Math.round(1 + t * (MAX_STACK - 1));
    }
}
