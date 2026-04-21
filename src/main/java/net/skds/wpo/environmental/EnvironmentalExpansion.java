package net.skds.wpo.environmental;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.skds.wpo.environmental.network.EnvPacketHandler;

@Mod(EnvironmentalExpansion.MOD_ID)
public class EnvironmentalExpansion {

    public static final String MOD_ID = "wpo_environmental_expansion";
    public static final String MOD_NAME = "WPO: Environmental Expansion";
    public static final Logger LOGGER = LogManager.getLogger();

    public EnvironmentalExpansion(IEventBus modBus, ModContainer container) {
        modBus.addListener(EnvironmentalContent::registerCapabilities);
        EnvironmentalContent.register(modBus);
        EnvironmentalConfig.init(container);
        EnvPacketHandler.init(modBus);
        NeoForge.EVENT_BUS.register(new EnvironmentalEvents());
        NeoForge.EVENT_BUS.register(BiomeScoutCommand.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EnvironmentalClientHooks.init(modBus, container);
        }
    }
}
