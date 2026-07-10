package com.createsatisfied.client.jei;

import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.config.CreateSatisfiedConfig;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.widgets.IRecipeWidget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;

/**
 * For machines where the rate genuinely never varies with rpm or batch size (e.g. the Spout's
 * fill time is a flat constant, unaffected by belt speed) - no slider to drag, just the number.
 * Still implements {@link IJeiInputHandler} purely so scroll-cycling the timeUnit config works
 * here too, consistent with every other widget in the mod.
 */
public class FixedRateWidget implements IRecipeWidget, IJeiInputHandler {

    public static final int EXTRA_HEIGHT = 20;

    private final Recipe<?> recipe;
    private final float opsPerMinute;
    private final int baseHeight;
    private final int width;

    public FixedRateWidget(Recipe<?> recipe, float opsPerMinute, int baseHeight, int width) {
        this.recipe = recipe;
        this.opsPerMinute = opsPerMinute;
        this.baseHeight = baseHeight;
        this.width = width;
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(0, baseHeight);
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Component label = Component.literal("Fixed rate: ~" + ThroughputFormat.formatItemRate(opsPerMinute));
        guiGraphics.drawString(Minecraft.getInstance().font, label, 8, 6, 0xFF404040, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        tooltip.add(Component.literal("Fixed rate - unaffected by rpm or belt speed").withStyle(ChatFormatting.GRAY));
        tooltip.addAll(ThroughputFormat.buildRateLines(recipe, opsPerMinute));
        if (CreateSatisfiedConfig.SCROLL_CHANGES_TIME_UNIT.get()) {
            tooltip.add(ThroughputFormat.scrollHintLine());
        }
    }

    @Override
    public ScreenRectangle getArea() {
        return new ScreenRectangle(0, baseHeight, width, EXTRA_HEIGHT);
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (scrollDeltaY == 0) {
            return false;
        }
        CreateSatisfiedConfig.cycleTimeUnit(scrollDeltaY > 0 ? 1 : -1);
        return true;
    }
}
