package net.skds.wpo.environmental.network;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class EnvClientKeyMappings {
    public static final KeyMapping OPEN_ENV_DEBUG = new KeyMapping(
        "key.wpo_environmental_expansion.open_environmental_debug",
        KeyConflictContext.UNIVERSAL,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F6,
        "key.categories.wpo_environmental_expansion"
    );

    private EnvClientKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ENV_DEBUG);
    }
}
