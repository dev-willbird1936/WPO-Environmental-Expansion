package net.skds.wpo.environmental.network;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;

/**
 * Client-side handler for the WPO Environmental debug overlay.
 * Displays real server-side data in an F3-style panel on the left side of the screen.
 */
public class EnvDebugOverlay {

    public static void render(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !EnvDebugData.hasData()) {
            return;
        }

        EnvDebugPacket d = EnvDebugData.getPacket();
        if (d == null) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int panelW = 195;
        int panelH = 260;
        int x = 10;
        int y = Math.min(16, screenHeight - panelH - 10);

        PoseStack emptyStack = new PoseStack();
        Gui.drawRect(emptyStack, x - 2, y - 2, x + panelW, y + panelH, 0x88000000);

        int cx = x + 4;
        int cy = y + 4;

        // Header
        cy = drawText(font, "WPO: Environmental", cx, cy, 0xFFFFFF);
        cy += 4;

        // Season info
        cy = drawText(font, "Season:", cx, cy, 0x909090);
        cy = drawText(font, formatSeason(d.subSeasonIndex(), d.tropicalPhaseIndex(), d.tropicalCycle(), d.seasonsEnabled()), cx + 50, cy, 0xFFFFFF);

        cy = drawText(font, "Time:", cx, cy, 0x909090);
        cy = drawText(font, formatTime(d.dayTime()), cx + 50, cy, 0xFFFFFF);

        cy = drawText(font, "Day:", cx, cy, 0x909090);
        cy = drawText(font, "Day " + d.worldDay(), cx + 50, cy, 0xFFFFFF);

        cy = drawText(font, "Cycle:", cx, cy, 0x909090);
        cy = drawText(font, d.tropicalCycle() ? "Tropical" : "Temperate", cx + 50, cy, 0x7FC8F8);
        cy += 4;

        // Biome
        cy = drawText(font, "Biome:", cx, cy, 0x909090);
        cy = drawText(font, shortenBiome(d.biomeId()), cx + 50, cy, 0xFFFFFF);

        cy = drawText(font, "Archetype:", cx, cy, 0x909090);
        cy = drawText(font, d.archetype(), cx + 50, cy, 0x7FC8F8);

        cy = drawText(font, "Temp:", cx, cy, 0x909090);
        cy = drawText(font, String.format("%.2f", d.biomeTemp()), cx + 50, cy, 0xFFFFFF);
        cy += 4;

        // Local block state
        cy = drawText(font, "Surface H2O:", cx, cy, 0x909090);
        cy = drawText(font, String.valueOf(d.surfaceWaterLevels()), cx + 50, cy, d.surfaceWaterLevels() > 0 ? 0x5599FF : 0xFFFFFF);

        cy = drawText(font, "Absorbed:", cx, cy, 0x909090);
        cy = drawText(font, String.valueOf(d.absorbedWater()), cx + 50, cy, d.absorbedWater() > 0 ? 0x77DD77 : 0xFFFFFF);

        cy = drawText(font, "Snow Layers:", cx, cy, 0x909090);
        cy = drawText(font, String.valueOf(d.snowLayers()), cx + 50, cy, d.snowLayers() > 0 ? 0xEEEEFF : 0xFFFFFF);

        String weatherStr = d.isThundering() ? "Thunder" : d.isRaining() ? "Rain" : "Clear";
        cy = drawText(font, "Weather:", cx, cy, 0x909090);
        cy = drawText(font, weatherStr, cx + 50, cy, d.isRaining() ? 0x5599FF : 0xFFFFFF);
        cy += 4;

        // Looking at
        cy = drawText(font, "Looking at:", cx, cy, 0x909090);
        cy = drawText(font, shortenBlock(d.targetBlock()), cx + 8, cy, d.targetBlock().isEmpty() ? 0x909090 : 0xFFFFFF);
        cy = drawText(font, formatPos(d.targetPos()), cx + 8, cy, 0x909090);
        cy += 4;

        // Separator
        Gui.drawRect(emptyStack, x, cy, x + panelW - 3, cy + 1, 0x444444);
        cy += 4;

        // Multipliers
        cy = drawText(font, "Multipliers", cx, cy, 0xFFFFFF);
        cy += 2;

        cy = drawText(font, "Rain:", cx, cy, 0x909090);
        cy = drawText(font, formatMult(d.rainMultiplier()), cx + 50, cy, multColor(d.rainMultiplier()));

        cy = drawText(font, "Evap:", cx, cy, 0x909090);
        cy = drawText(font, formatMult(d.evaporationMultiplier()), cx + 50, cy, multColor(d.evaporationMultiplier()));

        cy = drawText(font, "Absorb:", cx, cy, 0x909090);
        cy = drawText(font, formatMult(d.absorptionMultiplier()), cx + 50, cy, multColor(d.absorptionMultiplier()));

        cy = drawText(font, "Release:", cx, cy, 0x909090);
        cy = drawText(font, formatMult(d.releaseMultiplier()), cx + 50, cy, multColor(d.releaseMultiplier()));

        cy = drawText(font, "Snowmelt:", cx, cy, 0x909090);
        cy = drawText(font, formatMult(d.snowmeltMultiplier()), cx + 50, cy, multColor(d.snowmeltMultiplier()));

        cy = drawText(font, "Storm:", cx, cy, 0x909090);
        cy = drawText(font, formatMult(d.stormMultiplier()), cx + 50, cy, multColor(d.stormMultiplier()));
        cy += 4;

        // Separator
        Gui.drawRect(emptyStack, x, cy, x + panelW - 3, cy + 1, 0x444444);
        cy += 4;

        // Systems
        cy = drawText(font, "Systems", cx, cy, 0xFFFFFF);
        cy += 2;

        cy = drawText(font, "Drought:", cx, cy, 0x909090);
        cy = drawText(font, d.droughtActive() ? "ACTIVE" : "off", cx + 50, cy, d.droughtActive() ? 0xFF6600 : 0x909090);

        cy = drawText(font, "Active:", cx, cy, 0x909090);
        cy = drawText(font, buildSystemsStr(d), cx + 50, cy, 0xFFFFFF);

        // Data age warning
        long age = System.currentTimeMillis() - EnvDebugData.getLastReceiveTime();
        if (age > 5000) {
            drawText(font, "(!) data " + (age / 1000) + "s old", cx, cy + 6, 0xFF4444);
        }
    }

    private static int drawText(Font font, String text, int x, int y, int color) {
        if (text == null) text = "";
        font.draw(text, x, y, color);
        return y + 10;
    }

    private static String formatSeason(int subSeasonIndex, int tropicalPhase, boolean tropical, boolean seasonsEnabled) {
        if (!seasonsEnabled) return "Disabled";
        if (tropical) {
            return tropicalPhase == 0 ? "Wet Season" : "Dry Season";
        }
        String[] names = {
            "Early Spring", "Mid Spring", "Late Spring",
            "Early Summer", "Mid Summer", "Late Summer",
            "Early Autumn", "Mid Autumn", "Late Autumn",
            "Early Winter", "Mid Winter", "Late Winter"
        };
        int idx = Math.abs(subSeasonIndex % 12);
        return names[idx];
    }

    private static String formatTime(long dayTime) {
        long ticks = dayTime % 24000L;
        int hours = (int) (ticks / 1000);
        int minutes = (int) ((ticks % 1000) / 1000.0 * 60.0);
        String ampm = hours >= 12 ? "PM" : "AM";
        int displayHour = hours % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format("%d:%02d %s", displayHour, minutes, ampm);
    }

    private static String shortenBiome(String biomeId) {
        if (biomeId == null || biomeId.isEmpty()) return "???";
        String[] parts = biomeId.split(":");
        String path = parts[parts.length - 1];
        path = path.replaceAll("([a-z])([A-Z])", "$1 $2");
        return path;
    }

    private static String shortenBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) return "(none)";
        String[] parts = blockId.split(":");
        String path = parts[parts.length - 1];
        path = path.replaceAll("([a-z])([A-Z])", "$1 $2");
        return path.length() > 28 ? path.substring(0, 28) : path;
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return "(none)";
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatMult(double v) {
        return String.format("%.2f", v);
    }

    private static int multColor(double v) {
        if (v > 1.3) return 0xFF6666;
        if (v > 1.1) return 0xFFAA55;
        if (v < 0.7) return 0x6666FF;
        if (v < 0.9) return 0x55AAFF;
        return 0xE0E0E0;
    }

    private static String buildSystemsStr(EnvDebugPacket d) {
        StringBuilder sb = new StringBuilder();
        if (d.absorptionEnabled()) sb.append("Abs+");
        if (d.evaporationEnabled()) sb.append("Eva+");
        if (d.snowmeltEnabled()) sb.append("Snw+");
        if (d.floodsEnabled()) sb.append("Fld+");
        if (d.distantRainCatchupEnabled()) sb.append("DRC+");
        String s = sb.toString();
        return s.isEmpty() ? "all off" : s;
    }
}
