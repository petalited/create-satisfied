package com.createsatisfied.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

@Mixin(SpoutBlockEntity.class)
public interface SpoutBlockEntityAccessor {

    @Accessor("tank")
    SmartFluidTankBehaviour createsatisfied$getTank();
}
