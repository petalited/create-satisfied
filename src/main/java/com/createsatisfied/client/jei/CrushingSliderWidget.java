package com.createsatisfied.client.jei;

import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.config.CreateSatisfiedConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

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
import net.minecraft.world.level.block.Block;

/**
 * Crushing Wheels are the one machine where rpm alone isn't the interesting variable: a single
 * item always crushes at max speed regardless of rpm (log2(1) == 0 saturates the discount), so
 * an rpm-only slider would visibly do nothing most of the time. The discount only shows up once
 * you feed it a batch, so this exposes both rpm and stack size as separate draggable rows.
 */
public class CrushingSliderWidget implements IRecipeWidget, IJeiInputHandler {

    public static final int EXTRA_HEIGHT = 34;
    private static final int MAX_RPM = 256;
    private static final int MAX_STACK = 64;
    private static final int TRACK_MARGIN = 8;
    private static final int RPM_TRACK_Y = 6;
    private static final int STACK_TRACK_Y = 22;
    private static final int TRACK_HEIGHT = 4;
    private static final int ROW_SPLIT_Y = 14;
    private static final int HANDLE_OUTLINE = 0xFF1A1A1A;
    private static final int HANDLE_FILL = 0xFFE0E0E0;

    private final ProcessingRecipe<?, ?> recipe;
    private final Block stressBlock;
    private final int baseHeight;
    private final int width;
    private float rpm = 16f;
    private int stackSize = 1;

    public CrushingSliderWidget(ProcessingRecipe<?, ?> recipe, Block stressBlock, int baseHeight, int width) {
        this.recipe = recipe;
        this.stressBlock = stressBlock;
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

        drawTrack(guiGraphics, trackLeft, trackRight, RPM_TRACK_Y, rpm / MAX_RPM);
        drawTrack(guiGraphics, trackLeft, trackRight, STACK_TRACK_Y, (stackSize - 1) / (float) (MAX_STACK - 1));

        Component rpmLabel = Component.literal(Math.round(rpm) + " RPM");
        guiGraphics.drawString(Minecraft.getInstance().font, rpmLabel, trackLeft, RPM_TRACK_Y + 6, 0xFF404040, false);

        Component stackLabel = Component.literal(stackSize + (stackSize == 1 ? " item" : " items"));
        guiGraphics.drawString(Minecraft.getInstance().font, stackLabel, trackLeft, STACK_TRACK_Y + 6, 0xFF404040, false);
    }

    private void drawTrack(GuiGraphics guiGraphics, int trackLeft, int trackRight, int trackY, float t) {
        guiGraphics.fill(trackLeft, trackY, trackRight, trackY + TRACK_HEIGHT, 0xFF3A3A3A);
        int handleX = trackLeft + Math.round(Mth.clamp(t, 0f, 1f) * (trackRight - trackLeft));
        guiGraphics.fill(handleX - 2, trackY - 2, handleX + 2, trackY + TRACK_HEIGHT + 2, HANDLE_OUTLINE);
        guiGraphics.fill(handleX - 1, trackY - 1, handleX + 1, trackY + TRACK_HEIGHT + 1, HANDLE_FILL);
    }

    // The real block entity applies the recipe once its countdown drops below this threshold,
    // not below zero - see CrushingWheelControllerBlockEntity.tick().
    private static final float APPLY_THRESHOLD = 20f;

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        // CrushingWheelControllerBlockEntity stores rpm pre-divided by 50 (crushingspeed =
        // getSpeed()/50) before this same *4 multiplication - must match that scaling here too.
        float speed = Math.abs(rpm) / 50f * 4f;
        if (speed == 0) {
            tooltip.add(Component.literal("Stalled").withStyle(ChatFormatting.GRAY));
            tooltip.add(ThroughputFormat.scrollHintLine());
            return;
        }

        // log2(1) == 0, which would divide by zero - matches the real block entity's behavior of
        // always running at max speed for a single item, so we short-circuit the same way.
        float processingSpeed = stackSize <= 1
            ? 20f
            : Mth.clamp(speed / (float) (Math.log(stackSize) / Math.log(2)), 0.25f, 20f);

        int duration = recipe.getProcessingDuration();
        float ticksPerBatch = Math.max((duration - APPLY_THRESHOLD) / processingSpeed, 1f);
        float secondsPerBatch = ticksPerBatch / 20f;

        String header = String.format("Batch: %s for %d item%s", ThroughputFormat.formatDuration(ticksPerBatch, stackSize), stackSize, stackSize == 1 ? "" : "s");
        tooltip.add(Component.literal(header).withStyle(ChatFormatting.WHITE));
        tooltip.addAll(ThroughputFormat.buildBatchLines(recipe, stackSize, secondsPerBatch));
        // A working pair always has two wheels, each contributing their own stress impact.
        tooltip.add(ThroughputFormat.stressLine(stressBlock, rpm, 2));
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
        updateFromDrag(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        updateFromDrag(mouseX, mouseY);
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

    private void updateFromDrag(double mouseX, double mouseY) {
        float t = Mth.clamp((float) ((mouseX - TRACK_MARGIN) / (width - 2 * TRACK_MARGIN)), 0f, 1f);
        if (mouseY < ROW_SPLIT_Y) {
            rpm = Math.round(t * MAX_RPM);
        } else {
            stackSize = Math.round(1 + t * (MAX_STACK - 1));
        }
    }
}
