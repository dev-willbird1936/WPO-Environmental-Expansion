package net.skds.wpo.environmental;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.skds.wpo.environmental.config.EnvironmentalConfigScreen;

@Mod(EnvironmentalExpansion.MOD_ID)
public class EnvironmentalExpansion {

    public static final String MOD_ID = "wpo_environmental_expansion";
    public static final String MOD_NAME = "WPO: Environmental Expansion";
    public static final Logger LOGGER = LogManager.getLogger();

    public EnvironmentalExpansion() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::doClientSetup);
        EnvironmentalContent.register(modBus);
        EnvironmentalConfig.init();
        MinecraftForge.EVENT_BUS.register(new EnvironmentalEvents());
    }

    private void doClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
            container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(EnvironmentalConfigScreen::new))));
    }
}
