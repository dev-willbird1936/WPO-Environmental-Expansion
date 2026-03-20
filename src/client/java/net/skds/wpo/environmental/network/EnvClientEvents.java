package net.skds.wpo.environmental.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.EventBusSubscriber;

/**
 * Client-side event subscriber for the WPO Environmental debug overlay.
 * Renders the overlay when F3 is open.
 * Auto-registers via @EventBusSubscriber on the client side only.
 */
@EventBusSubscriber(modid = net.skds.wpo.environmental.EnvironmentalExpansion.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EnvClientEvents {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!Minecraft.getInstance().showDebugInfo()) return;
        EnvDebugOverlay.render(event.getMatrixStack());
    }
}
