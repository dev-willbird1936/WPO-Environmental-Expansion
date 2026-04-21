package net.skds.wpo.environmental;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * Biome scouting: nearest-biome search (findClosestBiome3d), teleport onto open surface (sky-visible), then patch sampling.
 * Usage: /wpo scoutbiomes [delaySeconds] [startIndex]
 */
public class BiomeScoutCommand {

    private static final String[] TERRALITH_BIOMES = {
        "terralith:alpha_islands", "terralith:alpha_islands_winter",
        "terralith:alpine_grove", "terralith:alpine_highlands", "terralith:amethyst_canyon",
        "terralith:amethyst_rainforest", "terralith:ancient_sands", "terralith:arid_highlands",
        "terralith:ashen_savanna", "terralith:basalt_cliffs", "terralith:birch_taiga",
        "terralith:blooming_plateau", "terralith:blooming_valley", "terralith:brushland",
        "terralith:bryce_canyon", "terralith:caldera", "terralith:cloud_forest",
        "terralith:cold_shrubland", "terralith:desert_canyon", "terralith:desert_oasis",
        "terralith:desert_spires", "terralith:emerald_peaks", "terralith:forested_highlands",
        "terralith:fractured_savanna", "terralith:frozen_cliffs", "terralith:glacial_chasm",
        "terralith:granite_cliffs", "terralith:gravel_beach", "terralith:gravel_desert",
        "terralith:haze_mountain", "terralith:highlands", "terralith:hot_shrubland",
        "terralith:ice_marsh", "terralith:jungle_mountains", "terralith:lavender_forest",
        "terralith:lavender_valley", "terralith:lush_desert", "terralith:lush_valley",
        "terralith:mirage_isles", "terralith:moonlight_grove", "terralith:moonlight_valley",
        "terralith:mountain_steppe", "terralith:orchid_swamp", "terralith:painted_mountains",
        "terralith:red_oasis", "terralith:rocky_jungle", "terralith:rocky_mountains",
        "terralith:rocky_shrubland", "terralith:sakura_grove", "terralith:sakura_valley",
        "terralith:sandstone_valley", "terralith:savanna_badlands", "terralith:savanna_slopes",
        "terralith:scarlet_mountains", "terralith:shield", "terralith:shield_clearing",
        "terralith:shrubland", "terralith:siberian_grove", "terralith:siberian_taiga",
        "terralith:skylands", "terralith:skylands_autumn", "terralith:skylands_spring",
        "terralith:skylands_summer", "terralith:skylands_winter", "terralith:snowy_badlands",
        "terralith:snowy_cherry_grove", "terralith:snowy_maple_forest", "terralith:snowy_shield",
        "terralith:steppe", "terralith:stony_spires", "terralith:temperate_highlands",
        "terralith:tropical_jungle", "terralith:valley_clearing", "terralith:volcanic_crater",
        "terralith:volcanic_peaks", "terralith:warm_river", "terralith:warped_mesa",
        "terralith:white_cliffs", "terralith:white_mesa", "terralith:windswept_spires",
        "terralith:wintry_forest", "terralith:wintry_lowlands", "terralith:yellowstone",
        "terralith:yosemite_cliffs", "terralith:yosemite_lowlands"
    };

    private static final int LOCATE_SEARCH_RADIUS = 6400;
    private static final int LOCATE_HORIZONTAL_STEP = 32;
    private static final int LOCATE_VERTICAL_STEP = 64;
    private static final long LOCATE_START_DELAY_SECONDS = 12L;

    // Scouting state
    private static volatile boolean scouting = false;
    private static volatile int currentIndex = 0;
    private static volatile int delaySeconds = 10;
    private static volatile ServerPlayer scoutPlayer = null;
    private static volatile MinecraftServer scoutServer = null;

    private static long locateTimeSumMs;
    private static int locateTimeCount;
    private static long locateTimeMinMs = Long.MAX_VALUE;
    private static long locateTimeMaxMs;

    // Scheduled task executor (survives world pause)
    private static final ScheduledThreadPoolExecutor SCHEDULER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "BiomeScout-Scheduler");
        t.setDaemon(true);
        return t;
    });

    // State machine
    // 0 = idle, 1 = locating, 2 = teleport to surface, 4 = sampling window
    private static int phase = 0;
    private static int locatedX = 0;
    private static int locatedZ = 0;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wpo")
            .then(Commands.literal("scoutbiomes")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> startScouting(ctx, 10, 0))
                .then(Commands.argument("delay", IntegerArgumentType.integer(5, 600))
                    .executes(ctx -> startScouting(ctx, IntegerArgumentType.getInteger(ctx, "delay"), 0))
                    .then(Commands.argument("start", IntegerArgumentType.integer(0, TERRALITH_BIOMES.length - 1))
                        .executes(ctx -> startScouting(ctx,
                            IntegerArgumentType.getInteger(ctx, "delay"),
                            IntegerArgumentType.getInteger(ctx, "start")))
                    )
                )
            )
            .then(Commands.literal("scoutstatus")
                .requires(source -> source.hasPermission(2))
                .executes(BiomeScoutCommand::showStatus)
            )
            .then(Commands.literal("scoutstop")
                .requires(source -> source.hasPermission(2))
                .executes(BiomeScoutCommand::stopScouting)
            )
        );
    }

    private static int startScouting(CommandContext<CommandSourceStack> ctx, int delay, int startIndex) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (scouting) {
            source.sendFailure(Component.literal("Scouting already in progress! Use /wpo scoutstop first."));
            return 0;
        }
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Must be in the Overworld to scout biomes."));
            return 0;
        }

        scouting = true;
        scoutPlayer = player;
        scoutServer = player.getServer();
        currentIndex = startIndex;
        delaySeconds = delay;
        phase = 0;
        locateTimeSumMs = 0L;
        locateTimeCount = 0;
        locateTimeMinMs = Long.MAX_VALUE;
        locateTimeMaxMs = 0L;

        int totalMin = (TERRALITH_BIOMES.length - startIndex) * delay / 60;
        source.sendSuccess(() -> Component.literal(ChatFormatting.GREEN + "Starting Terralith biome scout...")
            .append("\n" + ChatFormatting.YELLOW + "Biomes: " + TERRALITH_BIOMES.length
                + " | Delay: " + delay + "s | Start: " + startIndex
                + " | ETA: ~" + totalMin + "min"), true);

        schedule(LOCATE_START_DELAY_SECONDS, TimeUnit.SECONDS, BiomeScoutCommand::sendLocate);

        return TERRALITH_BIOMES.length - startIndex;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!scouting) {
            source.sendSuccess(() -> Component.literal("No scouting in progress."), false);
        } else {
            String current = currentIndex < TERRALITH_BIOMES.length ? TERRALITH_BIOMES[currentIndex] : "done";
            String phaseStr = switch (phase) {
                case 1 -> "awaiting locate";
                case 2 -> "teleporting";
                case 4 -> "sampling";
                default -> "idle";
            };
            source.sendSuccess(() -> Component.literal(
                "Scouting: " + ChatFormatting.GREEN + "ACTIVE"
                + ChatFormatting.WHITE + " | " + currentIndex + "/" + TERRALITH_BIOMES.length
                + " | " + current + " | " + phaseStr), false);
        }
        return 1;
    }

    private static int stopScouting(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        scouting = false;
        phase = 0;
        source.sendSuccess(() -> Component.literal(ChatFormatting.RED + "Scouting stopped at index " + currentIndex), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.isClientSide()) return;
        if (!scouting || scoutPlayer == null) return;

        if (scoutPlayer.isRemoved() || scoutPlayer.getServer() != scoutServer) {
            scouting = false;
            return;
        }
    }

    // === Scheduled callback methods (called by SCHEDULER, survive world pause) ===

    private static void schedule(long delay, TimeUnit unit, Runnable action) {
        SCHEDULER.schedule(() -> {
            MinecraftServer server = scoutServer;
            if (server != null) {
                server.execute(action);
            } else {
                action.run();
            }
        }, delay, unit);
    }

    private static void sendLocate() {
        if (!scouting || scoutPlayer == null || scoutServer == null) return;
        if (currentIndex >= TERRALITH_BIOMES.length) {
            scouting = false;
            String timing = formatLocateTimingSummary();
            EnvironmentalExpansion.LOGGER.info("[BiomeScout] run complete. {}", timing);
            scoutPlayer.sendSystemMessage(Component.literal(
                ChatFormatting.GREEN + "=== Biome scouting COMPLETE! ==="
                + ChatFormatting.WHITE + " All " + TERRALITH_BIOMES.length + " biomes processed."
                + ChatFormatting.GRAY + "\n" + timing));
            return;
        }

        String biomeId = TERRALITH_BIOMES[currentIndex];
        if (hasSamplingCoverage((ServerLevel) scoutPlayer.level(), biomeId)) {
            scoutPlayer.sendSystemMessage(Component.literal(
                ChatFormatting.DARK_GRAY + "Already covered: " + biomeId + " - skipping..."));
            currentIndex++;
            phase = 0;
            schedule(1, TimeUnit.SECONDS, BiomeScoutCommand::sendLocate);
            return;
        }

        scoutPlayer.sendSystemMessage(Component.literal(
            ChatFormatting.AQUA + "[" + (currentIndex + 1) + "/" + TERRALITH_BIOMES.length + "] "
            + ChatFormatting.WHITE + "Locating: " + biomeId));

        phase = 1;
        long t0 = System.nanoTime();
        Pair<BlockPos, Holder<Biome>> located = locateNearestBiome((ServerLevel) scoutPlayer.level(), biomeId);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        EnvironmentalExpansion.LOGGER.info("[BiomeScout] nearest-biome search {} -> {} in {} ms",
            biomeId, located != null ? "hit" : "miss", elapsedMs);
        if (located != null) {
            locateTimeSumMs += elapsedMs;
            locateTimeCount++;
            locateTimeMinMs = Math.min(locateTimeMinMs, elapsedMs);
            locateTimeMaxMs = Math.max(locateTimeMaxMs, elapsedMs);
        }
        if (located == null) {
            scoutPlayer.sendSystemMessage(Component.literal(
                ChatFormatting.RED + "Not found: " + TERRALITH_BIOMES[currentIndex] + " - skipping..."));
            currentIndex++;
            phase = 0;
            schedule(1, TimeUnit.SECONDS, BiomeScoutCommand::sendLocate);
            return;
        }

        BlockPos foundPos = located.getFirst();
        locatedX = foundPos.getX();
        locatedZ = foundPos.getZ();

        scoutPlayer.sendSystemMessage(Component.literal(
            ChatFormatting.GREEN + "Found " + TERRALITH_BIOMES[currentIndex]
            + ChatFormatting.GREEN + " at " + locatedX + ", " + locatedZ + " - teleporting..."));

        phase = 2;
        schedule(200, TimeUnit.MILLISECONDS, BiomeScoutCommand::teleportToSurfaceAtLocate);
    }

    private static String formatLocateTimingSummary() {
        if (locateTimeCount <= 0) {
            return "Nearest-biome search timing: no successful searches recorded.";
        }
        long avg = locateTimeSumMs / locateTimeCount;
        long min = locateTimeMinMs == Long.MAX_VALUE ? 0L : locateTimeMinMs;
        return "Nearest-biome search (ms): min=" + min + " avg=" + avg + " max=" + locateTimeMaxMs + " (n=" + locateTimeCount + ")";
    }

    private static Pair<BlockPos, Holder<Biome>> locateNearestBiome(ServerLevel level, String biomeId) {
        ResourceLocation biomeLocation = ResourceLocation.tryParse(biomeId);
        if (biomeLocation == null) return null;

        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeLocation);
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Optional<Holder.Reference<Biome>> biomeHolder = biomeRegistry.getHolder(biomeKey);
        if (biomeHolder.isEmpty()) return null;

        return level.findClosestBiome3d(
            holder -> holder.is(biomeKey),
            scoutPlayer.blockPosition(),
            LOCATE_SEARCH_RADIUS,
            LOCATE_HORIZONTAL_STEP,
            LOCATE_VERTICAL_STEP
        );
    }

    private static boolean hasSamplingCoverage(ServerLevel level, String biomeId) {
        ResourceLocation biomeLocation = ResourceLocation.tryParse(biomeId);
        return biomeLocation != null && BiomeProfileManager.hasSamplingCoverage(level, biomeLocation);
    }

    private static void teleportToSurfaceAtLocate() {
        if (!scouting || scoutPlayer == null) return;
        ServerLevel sl = (ServerLevel) scoutPlayer.level();
        double feetY = findFeetYInOpenAir(sl, locatedX, locatedZ);
        scoutPlayer.teleportTo(
            sl,
            locatedX + 0.5D, feetY, locatedZ + 0.5D,
            scoutPlayer.getYRot(), scoutPlayer.getXRot()
        );
        schedule(50, TimeUnit.MILLISECONDS, BiomeScoutCommand::nudgePlayerUntilOpenSurfaceAir);
    }

    /**
     * Feet Y on solid ground with two air blocks for feet/head and a clear column to the sky (not cave air).
     * Heightmap Y is the top matching block; standing air starts at Y+1.
     */
    private static double findFeetYInOpenAir(ServerLevel level, int x, int z) {
        Double y = scanSurfaceFeetY(level, x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 320);
        if (y != null) {
            return y;
        }
        y = scanSurfaceFeetY(level, x, z, Heightmap.Types.WORLD_SURFACE, 96);
        if (y != null) {
            return y;
        }
        int topSolid = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (topSolid <= level.getMinBuildHeight()) {
            topSolid = level.getSeaLevel();
        }
        return topSolid + 1 + 0.01D;
    }

    private static Double scanSurfaceFeetY(ServerLevel level, int x, int z, Heightmap.Types heightmapType, int maxRise) {
        int topSolid = level.getHeight(heightmapType, x, z);
        if (topSolid <= level.getMinBuildHeight()) {
            return null;
        }
        int footStart = topSolid + 1;
        int ceiling = Math.min(level.getMaxBuildHeight() - 3, footStart + maxRise);
        for (int footBlockY = footStart; footBlockY < ceiling; footBlockY++) {
            BlockPos feet = new BlockPos(x, footBlockY, z);
            if (!level.hasChunkAt(feet)) {
                continue;
            }
            BlockState ground = level.getBlockState(feet.below());
            BlockState atFeet = level.getBlockState(feet);
            BlockState atHead = level.getBlockState(feet.above());
            if (!ground.blocksMotion()) {
                continue;
            }
            if (atFeet.blocksMotion() || atHead.blocksMotion()) {
                continue;
            }
            if (!atFeet.getFluidState().isEmpty() || !atHead.getFluidState().isEmpty()) {
                continue;
            }
            if (!level.canSeeSky(feet)) {
                continue;
            }
            return footBlockY + 0.01D;
        }
        return null;
    }

    private static void nudgePlayerUntilOpenSurfaceAir() {
        if (!scouting || scoutPlayer == null) return;
        ServerLevel sl = (ServerLevel) scoutPlayer.level();
        double x = locatedX + 0.5D;
        double z = locatedZ + 0.5D;
        for (int step = 0; step < 256; step++) {
            if (playerHasOpenSurfaceAirToStand(sl, scoutPlayer)) {
                beginSamplingWindow();
                return;
            }
            double ny = scoutPlayer.getY() + 1.0D;
            scoutPlayer.teleportTo(sl, x, ny, z, scoutPlayer.getYRot(), scoutPlayer.getXRot());
        }
        double feetY = findFeetYInOpenAir(sl, locatedX, locatedZ);
        scoutPlayer.teleportTo(sl, x, feetY, z, scoutPlayer.getYRot(), scoutPlayer.getXRot());
        if (playerHasOpenSurfaceAirToStand(sl, scoutPlayer)) {
            beginSamplingWindow();
        } else {
            EnvironmentalExpansion.LOGGER.warn(
                "[BiomeScout] no open-surface (sky-visible) stand at {}, {} after nudge — skipping 15s window",
                locatedX, locatedZ);
            scoutPlayer.sendSystemMessage(Component.literal(
                ChatFormatting.RED + "No open surface at this column — skipping observe, next biome..."));
            advanceScoutPastCurrentBiome();
        }
    }

    private static void advanceScoutPastCurrentBiome() {
        if (!scouting) return;
        currentIndex++;
        phase = 0;
        schedule(1, TimeUnit.SECONDS, BiomeScoutCommand::sendLocate);
    }

    /** Open air with sky access at the feet column (surface / under open sky, not underground cave pockets). */
    private static boolean playerHasOpenSurfaceAirToStand(ServerLevel level, ServerPlayer player) {
        if (player.isInWall()) {
            return false;
        }
        if (!level.noCollision(player)) {
            return false;
        }
        BlockPos feet = player.blockPosition();
        return level.canSeeSky(feet);
    }

    private static void beginSamplingWindow() {
        if (!scouting || scoutPlayer == null) return;
        ServerLevel sl = (ServerLevel) scoutPlayer.level();
        BiomeProfileManager.scoutObserve(sl, locatedX, locatedZ);
        int d = Math.max(5, delaySeconds);
        long t1 = Math.max(1L, Math.round(d * 0.08));
        long t2 = Math.max(2L, Math.round(d * 0.35));
        long t3 = Math.max(3L, Math.round(d * 0.65));
        schedule(t1, TimeUnit.SECONDS, () -> runScoutObserveIfActive(sl));
        schedule(t2, TimeUnit.SECONDS, () -> runScoutObserveIfActive(sl));
        schedule(t3, TimeUnit.SECONDS, () -> runScoutObserveIfActive(sl));
        scoutPlayer.sendSystemMessage(Component.literal(
            ChatFormatting.GREEN + "Open surface — observing " + delaySeconds + "s (started now)..."
            + ChatFormatting.GRAY + " | " + locatedX + ", " + (int) Math.floor(scoutPlayer.getY()) + ", " + locatedZ));
        phase = 4;
        schedule(delaySeconds, TimeUnit.SECONDS, BiomeScoutCommand::finishSampling);
    }

    private static void runScoutObserveIfActive(ServerLevel sl) {
        if (!scouting || scoutPlayer == null || scoutPlayer.level() != sl) return;
        BiomeProfileManager.scoutObserve(sl, locatedX, locatedZ);
    }

    private static void finishSampling() {
        if (!scouting) return;
        currentIndex++;
        phase = 0;
        schedule(1, TimeUnit.SECONDS, BiomeScoutCommand::sendLocate);
    }

}
