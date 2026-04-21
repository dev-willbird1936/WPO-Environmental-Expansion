package net.skds.wpo.environmental;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.skds.wpo.environmental.config.EnvironmentalConfigScreen;
import net.skds.wpo.environmental.network.EnvClientEvents;
import net.skds.wpo.environmental.network.EnvClientKeyMappings;

final class EnvironmentalClientHooks {

    private EnvironmentalClientHooks() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(EnvironmentalContent.SURFACE_ICE.get(), RenderType.translucent());
            container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, modListScreen) -> new EnvironmentalConfigScreen(modListScreen)
            );
        }));
        NeoForge.EVENT_BUS.register(EnvClientEvents.class);
        modBus.register(EnvClientKeyMappings.class);
    }
}
