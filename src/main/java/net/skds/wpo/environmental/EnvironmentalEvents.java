package net.skds.wpo.environmental;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EnvironmentalEvents {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent event) {
        if (event.phase != Phase.END || !(event.level instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) {
            return;
        }
        EnvironmentalTicker.tick(serverLevel);
    }
}
