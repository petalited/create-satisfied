package com.createsatisfied.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;

import net.minecraft.world.item.crafting.Recipe;

@Mixin(BasinOperatingBlockEntity.class)
public interface BasinOperatingBlockEntityAccessor {

    @Accessor("currentRecipe")
    Recipe<?> createsatisfied$getCurrentRecipe();
}
