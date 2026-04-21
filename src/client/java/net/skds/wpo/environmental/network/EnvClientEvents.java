package net.skds.wpo.environmental.network;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Client-side event subscriber for the WPO Environmental debug overlay.
 * Renders the overlay when F3 is open and handles the F6 debug menu toggle.
 */
public class EnvClientEvents {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.DEBUG_OVERLAY)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EnvDebugScreen) {
            return;
        }

        EnvDebugOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (EnvClientKeyMappings.OPEN_ENV_DEBUG.consumeClick()) {
            EnvDebugScreen.toggle();
        }
    }
}
