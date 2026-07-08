package net.skds.wpo.environmental;

import java.nio.file.Paths;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class EnvironmentalClientConfig {

    public static final Client CLIENT;
    private static final ModConfigSpec SPEC;

    static {
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        SPEC = client.getRight();
    }

    private EnvironmentalClientConfig() {
    }

    public static void init(ModContainer container) {
        Paths.get(System.getProperty("user.dir"), "config", EnvironmentalExpansion.MOD_ID).toFile().mkdirs();
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, Paths.get(EnvironmentalExpansion.MOD_ID, "client.toml").toString());
    }

    public static boolean isCompactDebugOverlayEnabled() {
        return CLIENT.compactDebugOverlay.get();
    }

    public static void setCompactDebugOverlayEnabled(boolean enabled) {
        CLIENT.compactDebugOverlay.set(enabled);
        SPEC.save();
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue compactDebugOverlay;

        private Client(ModConfigSpec.Builder builder) {
            Function<String, ModConfigSpec.Builder> translate =
                key -> builder.translation(EnvironmentalExpansion.MOD_ID + ".config.client." + key);

            builder.push("debugUi");
            compactDebugOverlay = translate.apply("compactDebugOverlay")
                .comment("Shows the environmental lines in the vanilla-style F3 debug HUD when enabled.")
                .define("compactDebugOverlay", false);
            builder.pop();
        }
    }
}
