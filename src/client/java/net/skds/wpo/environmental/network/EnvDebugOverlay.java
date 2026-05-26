package net.skds.wpo.environmental.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;

/**
 * Client-side handler for the WPO Environmental debug overlay.
 * Draws a plain F3-style environmental summary when enabled.
 */
public class EnvDebugOverlay {
    private static final int LINE_GAP = 2;
    private static final int BASE_LINE_OFFSET = 8;
    private static final int DEBUG_BG = 0x90505050;
    private static final int TEXT_COLOR = 0xE0E0E0;
    private static final int STALE_COLOR = 0xFF8080;

    public static void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        EnvDebugPacket d = EnvDebugData.getPacket();
        boolean noData = !EnvDebugData.hasData() || d == null;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        if (noData) {
            int baseY = EnvDebugOverlayStack.reserveLeft(mc, font, BASE_LINE_OFFSET, LINE_GAP, 2);
            renderLeft(guiGraphics, font, List.of(
                "WPO Environmental [F6]",
                "Waiting for server debug data..."
            ), 2, baseY, screenWidth);
            return;
        }

        long ageMs = System.currentTimeMillis() - EnvDebugData.getLastReceiveTime();
        String ageText = EnvDebugUi.formatAge(ageMs);

        List<String> leftLines = List.of(
            "WPO Environmental [F6]",
            "Time: " + EnvDebugUi.formatTime(d.dayTime()) + "  Day: " + d.worldDay(),
            "Season: " + EnvDebugUi.formatSeason(d.subSeasonIndex(), d.tropicalPhaseIndex(), d.tropicalCycle(), d.seasonsEnabled()),
            "Weather: " + EnvDebugUi.formatWeather(d.isRaining(), d.isThundering()) + "  Age: " + ageText,
            "Biome: " + EnvDebugUi.shortenBiome(d.biomeId()),
            "Archetype: " + EnvDebugUi.formatEnumName(d.archetype()),
            "Target: " + EnvDebugUi.shortenBlock(d.targetBlock()),
            "Pos: " + EnvDebugUi.formatPos(d.targetPos()),
            "Surface: H2O " + d.surfaceWaterLevels() + "  Abs " + d.absorbedWater() + "  Snow " + d.snowLayers() + "  Ice " + d.surfaceIceLevels(),
            "Soil: " + EnvDebugUi.formatMoisture(d.farmlandMoisture()) + "  Systems: " + EnvDebugUi.buildSystemsStr(d)
        );
        List<String> rightLines = List.of(
            String.format(Locale.ROOT, "Air: %.1f C  Humidity: %.0f%%", d.realTempC(), d.realHumidityPct()),
            String.format(Locale.ROOT, "Wind: %.1f m/s", d.realWindMs()),
            "Precip: " + EnvDebugUi.formatPercent(d.precipChancePct()) + String.format(Locale.ROOT, "  %.1f mm/h", d.precipMmHr()),
            "Condense: " + EnvDebugUi.formatPercent(d.condensationChancePct()),
            "Freeze: " + EnvDebugUi.formatPercent(d.freezingChancePct()) + "  Thaw: " + EnvDebugUi.formatPercent(d.thawChancePct()),
            "Crop Boost: " + EnvDebugUi.formatPercent(d.agricultureGrowthChancePct()),
            String.format(Locale.ROOT, "Rain x: %.2f  Evap x: %.2f", d.rainMultiplier(), d.evaporationMultiplier()),
            String.format(Locale.ROOT, "Absorb x: %.2f  Release x: %.2f", d.absorptionMultiplier(), d.releaseMultiplier()),
            String.format(Locale.ROOT, "Snowmelt x: %.2f  Storm x: %.2f", d.snowmeltMultiplier(), d.stormMultiplier()),
            "Drought: " + (d.droughtActive() ? "ACTIVE" : "off")
        );

        boolean hasStaleLine = ageMs > 5000L;
        int baseY = EnvDebugOverlayStack.reserveBoth(mc, font, BASE_LINE_OFFSET, LINE_GAP, leftLines.size() + (hasStaleLine ? 1 : 0), rightLines.size());
        int leftY = renderLeft(guiGraphics, font, leftLines, 2, baseY, screenWidth);
        renderRight(guiGraphics, font, rightLines, screenWidth - 2, baseY, hasStaleLine ? 1 : -1);
        if (hasStaleLine) {
            renderLeft(guiGraphics, font, List.of("Data is stale; waiting for the next server sync."), 2, leftY + LINE_GAP, screenWidth, STALE_COLOR);
        }
    }

    private static int renderLeft(GuiGraphics guiGraphics, Font font, List<String> lines, int x, int y, int screenWidth) {
        return renderLeft(guiGraphics, font, lines, x, y, screenWidth, TEXT_COLOR);
    }

    private static int renderLeft(GuiGraphics guiGraphics, Font font, List<String> lines, int x, int y, int screenWidth, int color) {
        int currentY = y;
        for (String line : lines) {
            drawLine(guiGraphics, font, line, x, currentY, color);
            currentY += font.lineHeight + LINE_GAP;
        }
        return currentY;
    }

    private static void renderRight(GuiGraphics guiGraphics, Font font, List<String> lines, int rightX, int y, int staleIndex) {
        int currentY = y;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int width = font.width(line);
            int x = rightX - width;
            drawLine(guiGraphics, font, line, x, currentY, i == staleIndex ? STALE_COLOR : TEXT_COLOR);
            currentY += font.lineHeight + LINE_GAP;
        }
    }

    private static void drawLine(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color) {
        int width = font.width(text);
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + font.lineHeight + 1, DEBUG_BG);
        guiGraphics.drawString(font, text, x, y, color, false);
    }
}
