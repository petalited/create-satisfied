package com.createsatisfied.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.createsatisfied.client.SpeedFormula;
import com.createsatisfied.client.ThroughputFormat;
import com.createsatisfied.client.jei.CrushingSliderWidget;
import com.createsatisfied.client.jei.FanStackSliderWidget;
import com.createsatisfied.client.jei.FixedRateWidget;
import com.createsatisfied.client.jei.RpmSliderWidget;
import com.createsatisfied.client.jei.SequencedAssemblySliderWidget;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.CrushingCategory;
import com.simibubi.create.compat.jei.category.DeployingCategory;
import com.simibubi.create.compat.jei.category.FanBlastingCategory;
import com.simibubi.create.compat.jei.category.FanHauntingCategory;
import com.simibubi.create.compat.jei.category.FanSmokingCategory;
import com.simibubi.create.compat.jei.category.FanWashingCategory;
import com.simibubi.create.compat.jei.category.MillingCategory;
import com.simibubi.create.compat.jei.category.MixingCategory;
import com.simibubi.create.compat.jei.category.PackingCategory;
import com.simibubi.create.compat.jei.category.PressingCategory;
import com.simibubi.create.compat.jei.category.SawingCategory;
import com.simibubi.create.compat.jei.category.SequencedAssemblyCategory;
import com.simibubi.create.compat.jei.category.SpoutCategory;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

@Mixin(CreateRecipeCategory.class)
public abstract class CreateRecipeCategoryMixin<T extends Recipe<?>> {

    @Shadow
    @Final
    protected IDrawable background;

    public int getHeight() {
        int base = this.background.getHeight();
        if ((Object) this instanceof CrushingCategory) {
            return base + CrushingSliderWidget.EXTRA_HEIGHT;
        }
        if ((Object) this instanceof SequencedAssemblyCategory) {
            return base + SequencedAssemblySliderWidget.EXTRA_HEIGHT;
        }
        if (resolveFormula(this) != null || isFanCategory(this) || (Object) this instanceof SpoutCategory) {
            return base + RpmSliderWidget.EXTRA_HEIGHT;
        }
        return base;
    }

    public void createRecipeExtras(IRecipeExtrasBuilder builder, Object recipeHolder, IFocusGroup focuses) {
        if (!(recipeHolder instanceof RecipeHolder<?> holder)) {
            return;
        }
        Recipe<?> recipeValue = holder.value();

        int baseHeight = this.background.getHeight();
        int width = this.background.getWidth();

        if ((Object) this instanceof CrushingCategory && recipeValue instanceof ProcessingRecipe<?, ?> crushingRecipe) {
            CrushingSliderWidget widget = new CrushingSliderWidget(crushingRecipe, AllBlocks.CRUSHING_WHEEL.get(), baseHeight, width);
            builder.addWidget(widget);
            builder.addInputHandler(widget);
            return;
        }

        if ((Object) this instanceof SequencedAssemblyCategory && recipeValue instanceof SequencedAssemblyRecipe sequencedRecipe) {
            SequencedAssemblySliderWidget widget = new SequencedAssemblySliderWidget(sequencedRecipe, baseHeight, width);
            builder.addWidget(widget);
            builder.addInputHandler(widget);
            return;
        }

        if (isFanCategory(this)) {
            FanStackSliderWidget widget = new FanStackSliderWidget(recipeValue, baseHeight, width);
            builder.addWidget(widget);
            builder.addInputHandler(widget);
            return;
        }

        if ((Object) this instanceof SpoutCategory) {
            float opsPerMinute = ThroughputFormat.TICKS_PER_MINUTE / (float) SpoutBlockEntity.FILLING_TIME;
            FixedRateWidget widget = new FixedRateWidget(recipeValue, opsPerMinute, baseHeight, width);
            builder.addWidget(widget);
            builder.addInputHandler(widget);
            return;
        }

        SpeedFormula formula = resolveFormula(this);
        if (formula != null && recipeValue instanceof ProcessingRecipe<?, ?> recipe) {
            RpmSliderWidget widget = new RpmSliderWidget(recipe, formula, resolveStressBlock(this), baseHeight, width);
            builder.addWidget(widget);
            builder.addInputHandler(widget);
        }
    }

    private static SpeedFormula resolveFormula(Object category) {
        if (category instanceof MillingCategory) {
            return SpeedFormula.continuous(16f, 1f, 512f);
        }
        if (category instanceof SawingCategory) {
            return SpeedFormula.continuous(24f, 1f, 128f);
        }
        if (category instanceof PressingCategory || category instanceof PackingCategory) {
            return SpeedFormula.press();
        }
        if (category instanceof MixingCategory) {
            return SpeedFormula.mixer();
        }
        if (category instanceof DeployingCategory) {
            return SpeedFormula.deployer();
        }
        return null;
    }

    // Stress impact is a per-block config value, entirely independent of which formula the
    // category uses for throughput - kept separate since Pressing and Packing share a formula
    // but are different physical blocks (world/belt Press vs. Press-over-basin).
    private static Block resolveStressBlock(Object category) {
        if (category instanceof MillingCategory) {
            return AllBlocks.MILLSTONE.get();
        }
        if (category instanceof SawingCategory) {
            return AllBlocks.MECHANICAL_SAW.get();
        }
        if (category instanceof PressingCategory || category instanceof PackingCategory) {
            return AllBlocks.MECHANICAL_PRESS.get();
        }
        if (category instanceof MixingCategory) {
            return AllBlocks.MECHANICAL_MIXER.get();
        }
        if (category instanceof DeployingCategory) {
            return AllBlocks.DEPLOYER.get();
        }
        return null;
    }

    private static boolean isFanCategory(Object category) {
        return category instanceof FanWashingCategory
            || category instanceof FanSmokingCategory
            || category instanceof FanBlastingCategory
            || category instanceof FanHauntingCategory;
    }
}
