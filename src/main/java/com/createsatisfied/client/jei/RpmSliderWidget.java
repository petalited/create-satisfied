package com.createsatisfied.client.jei;

import com.createsatisfied.client.SpeedFormula;
import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.config.CreateSatisfiedConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

public class RpmSliderWidget implements IRecipeWidget, IJeiInputHandler {

    public static final int EXTRA_HEIGHT = 20;
    private static final int MAX_RPM = 256;
    private static final int TRACK_MARGIN = 8;
    private static final int TRACK_Y = 6;
    private static final int TRACK_HEIGHT = 4;
    private static final int HANDLE_OUTLINE = 0xFF1A1A1A;
    private static final int HANDLE_FILL = 0xFFE0E0E0;

    private final ProcessingRecipe<?, ?> recipe;
    private final SpeedFormula formula;
    private final Block stressBlock;
    private final int baseHeight;
    private final int width;
    private float rpm = 16f;

    public RpmSliderWidget(ProcessingRecipe<?, ?> recipe, SpeedFormula formula, Block stressBlock, int baseHeight, int width) {
        this.recipe = recipe;
        this.formula = formula;
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
        guiGraphics.fill(trackLeft, TRACK_Y, trackRight, TRACK_Y + TRACK_HEIGHT, 0xFF3A3A3A);

        float t = Mth.clamp(rpm / MAX_RPM, 0f, 1f);
        int handleX = trackLeft + Math.round(t * (trackRight - trackLeft));
        guiGraphics.fill(handleX - 2, TRACK_Y - 2, handleX + 2, TRACK_Y + TRACK_HEIGHT + 2, HANDLE_OUTLINE);
        guiGraphics.fill(handleX - 1, TRACK_Y - 1, handleX + 1, TRACK_Y + TRACK_HEIGHT + 1, HANDLE_FILL);

        Component label = Component.literal(Math.round(rpm) + " RPM");
        guiGraphics.drawString(Minecraft.getInstance().font, label, trackLeft, TRACK_Y + 6, 0xFF404040, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        float opsPerMinute = formula.opsPerMinute(rpm, recipe);
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        tooltip.add(ThroughputFormat.stressLine(stressBlock, rpm));
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
