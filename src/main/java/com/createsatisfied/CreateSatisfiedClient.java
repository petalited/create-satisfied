package com.createsatisfied;

import com.createsatisfied.client.overlay.GoggleThroughputOverlay;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CreateSatisfied.MODID, dist = Dist.CLIENT)
public class CreateSatisfiedClient {
    public CreateSatisfiedClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(GoggleThroughputOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(GoggleThroughputOverlay::onMouseScroll);
        // Opts into NeoForge's built-in generic config screen (auto-generated from the registered
        // ModConfigSpec) instead of hand-rolling one - reachable from the mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
