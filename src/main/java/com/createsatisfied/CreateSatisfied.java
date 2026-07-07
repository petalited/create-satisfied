package com.createsatisfied;

import org.slf4j.Logger;

import com.createsatisfied.config.CreateSatisfiedConfig;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CreateSatisfied.MODID)
public class CreateSatisfied {
    public static final String MODID = "createsatisfied";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateSatisfied(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, CreateSatisfiedConfig.SPEC);
    }
}
