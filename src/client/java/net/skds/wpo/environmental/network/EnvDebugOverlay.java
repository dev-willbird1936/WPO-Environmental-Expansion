package net.skds.wpo.environmental.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Client-side handler for the WPO Environmental debug overlay.
 * Displays a compact F3-style summary on the left side of the screen.
 */
public class EnvDebugOverlay {

    public static void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        EnvDebugPacket d = EnvDebugData.getPacket();
        boolean noData = !EnvDebugData.hasData() || d == null;

        Font font = mc.font;
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int panelW = 214;
        int panelH = 450;
        int x = 10;
        int y = Math.max(10, Math.min(16, screenHeight - panelH - 10));

        guiGraphics.fill(x - 2, y - 2, x + panelW, y + panelH, 0x88000000);

        int cx = x + 4;
        int cy = y + 4;

        cy = drawText(guiGraphics, font, "WPO: Environmental", cx, cy, 0xFFFFFF);
        cy += 4;

        if (!noData) {
            cy = drawText(guiGraphics, font, "Season:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatSeason(d.subSeasonIndex(), d.tropicalPhaseIndex(), d.tropicalCycle(), d.seasonsEnabled()), cx + 50, cy, 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Time:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatTime(d.dayTime()), cx + 50, cy, 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Day:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, "Day " + d.worldDay(), cx + 50, cy, 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Cycle:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, d.tropicalCycle() ? "Tropical" : "Temperate", cx + 50, cy, 0x7FC8F8);
            cy += 4;

            cy = drawText(guiGraphics, font, "Biome:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.shortenBiome(d.biomeId()), cx + 50, cy, 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Archetype:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatEnumName(d.archetype()), cx + 50, cy, 0x7FC8F8);

            cy = drawText(guiGraphics, font, "Temp:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.format(Locale.ROOT, "%.2f", d.biomeTemp()), cx + 50, cy, 0xFFFFFF);
            cy += 4;

            cy = drawText(guiGraphics, font, "Surface H2O:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.valueOf(d.surfaceWaterLevels()), cx + 50, cy, d.surfaceWaterLevels() > 0 ? 0x5599FF : 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Absorbed:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.valueOf(d.absorbedWater()), cx + 50, cy, d.absorbedWater() > 0 ? 0x77DD77 : 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Snow Layers:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.valueOf(d.snowLayers()), cx + 50, cy, d.snowLayers() > 0 ? 0xEEEEFF : 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Surface Ice:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.valueOf(d.surfaceIceLevels()), cx + 50, cy, d.surfaceIceLevels() > 0 ? 0xB9D8FF : 0xFFFFFF);

            cy = drawText(guiGraphics, font, "Soil Moist:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMoisture(d.farmlandMoisture()), cx + 50, cy, EnvDebugUi.moistureColor(d.farmlandMoisture()));

            String weatherStr = d.isThundering() ? "Thunder" : d.isRaining() ? "Rain" : "Clear";
            cy = drawText(guiGraphics, font, "Weather:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, weatherStr, cx + 50, cy, d.isRaining() ? 0x5599FF : 0xFFFFFF);
            cy += 4;

            cy = drawText(guiGraphics, font, "Real World", cx, cy, 0xFFFFFF);
            cy += 2;

            cy = drawText(guiGraphics, font, "Temp:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.format(Locale.ROOT, "%.1f C", d.realTempC()), cx + 50, cy, EnvDebugUi.tempColor(d.realTempC()));

            cy = drawText(guiGraphics, font, "Humidity:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPercent(d.realHumidityPct()), cx + 50, cy, EnvDebugUi.humidityColor(d.realHumidityPct()));

            cy = drawText(guiGraphics, font, "Wind:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.format(Locale.ROOT, "%.1f m/s", d.realWindMs()), cx + 50, cy, EnvDebugUi.windColor(d.realWindMs()));

            cy = drawText(guiGraphics, font, "Precip ch:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPercent(d.precipChancePct()), cx + 50, cy, EnvDebugUi.chanceColor(d.precipChancePct()));

            cy = drawText(guiGraphics, font, "Precip:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, String.format(Locale.ROOT, "%.1f mm/h", d.precipMmHr()), cx + 50, cy, EnvDebugUi.precipColor(d.precipMmHr()));

            cy = drawText(guiGraphics, font, "Condense:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPercent(d.condensationChancePct()), cx + 50, cy, EnvDebugUi.chanceColor(d.condensationChancePct()));

            cy = drawText(guiGraphics, font, "Freeze:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPercent(d.freezingChancePct()), cx + 50, cy, EnvDebugUi.chanceColor(d.freezingChancePct()));

            cy = drawText(guiGraphics, font, "Thaw:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPercent(d.thawChancePct()), cx + 50, cy, EnvDebugUi.chanceColor(d.thawChancePct()));

            cy = drawText(guiGraphics, font, "Crop+:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPercent(d.agricultureGrowthChancePct()), cx + 50, cy, EnvDebugUi.chanceColor(d.agricultureGrowthChancePct()));
            cy += 4;

            cy = drawText(guiGraphics, font, "Looking at:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.shortenBlock(d.targetBlock()), cx + 8, cy, d.targetBlock().isEmpty() ? 0x909090 : 0xFFFFFF);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatPos(d.targetPos()), cx + 8, cy, 0x909090);
            cy += 4;

            guiGraphics.fill(x, cy, x + panelW - 3, cy + 1, 0x444444);
            cy += 4;

            cy = drawText(guiGraphics, font, "Multipliers", cx, cy, 0xFFFFFF);
            cy += 2;

            cy = drawText(guiGraphics, font, "Rain:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMult(d.rainMultiplier()), cx + 50, cy, EnvDebugUi.multColor(d.rainMultiplier()));

            cy = drawText(guiGraphics, font, "Evap:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMult(d.evaporationMultiplier()), cx + 50, cy, EnvDebugUi.multColor(d.evaporationMultiplier()));

            cy = drawText(guiGraphics, font, "Absorb:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMult(d.absorptionMultiplier()), cx + 50, cy, EnvDebugUi.multColor(d.absorptionMultiplier()));

            cy = drawText(guiGraphics, font, "Release:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMult(d.releaseMultiplier()), cx + 50, cy, EnvDebugUi.multColor(d.releaseMultiplier()));

            cy = drawText(guiGraphics, font, "Snowmelt:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMult(d.snowmeltMultiplier()), cx + 50, cy, EnvDebugUi.multColor(d.snowmeltMultiplier()));

            cy = drawText(guiGraphics, font, "Storm:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.formatMult(d.stormMultiplier()), cx + 50, cy, EnvDebugUi.multColor(d.stormMultiplier()));
            cy += 4;

            guiGraphics.fill(x, cy, x + panelW - 3, cy + 1, 0x444444);
            cy += 4;

            cy = drawText(guiGraphics, font, "Systems", cx, cy, 0xFFFFFF);
            cy += 2;

            cy = drawText(guiGraphics, font, "Drought:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, d.droughtActive() ? "ACTIVE" : "off", cx + 50, cy, d.droughtActive() ? 0xFF6600 : 0x909090);

            cy = drawText(guiGraphics, font, "Active:", cx, cy, 0x909090);
            cy = drawText(guiGraphics, font, EnvDebugUi.buildSystemsStr(d), cx + 50, cy, 0xFFFFFF);
            cy += 4;

            guiGraphics.fill(x, cy, x + panelW - 3, cy + 1, 0x444444);
            cy += 4;
        } else {
            cy = drawText(guiGraphics, font, "Waiting for data...", cx, cy, 0x909090);
            cy += 4;
        }

        drawText(guiGraphics, font, "F6: climate menu", cx, cy + 2, 0x909090);
    }

    private static int drawText(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color) {
        if (text == null) {
            text = "";
        }
        guiGraphics.drawString(font, Component.literal(text), x, y, color, false);
        return y + 10;
    }
}
