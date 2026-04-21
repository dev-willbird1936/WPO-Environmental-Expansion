package net.skds.wpo.environmental.network;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class EnvDebugScreen extends Screen {
    private final Screen parentScreen;

    public EnvDebugScreen(Screen parentScreen) {
        super(Component.translatable("screen.wpo_environmental_expansion.environmental_debug.title"));
        this.parentScreen = parentScreen;
    }

    static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (mc.screen instanceof EnvDebugScreen screen) {
            mc.setScreen(screen.parentScreen);
            return;
        }

        mc.setScreen(new EnvDebugScreen(mc.screen));
    }

    Screen parentScreen() {
        return parentScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_F6) {
            toggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        EnvDebugPacket packet = EnvDebugData.getPacket();
        boolean noData = !EnvDebugData.hasData() || packet == null;

        int panelW = 430;
        int panelH = 344;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        guiGraphics.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0xFF4C5563);
        guiGraphics.fill(x, y, x + panelW, y + panelH, 0xCC11161D);
        guiGraphics.fill(x + 1, y + 1, x + panelW - 1, y + 3, 0xFF56D0C0);

        int centerX = x + panelW / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, y + 8, 0xFFFFFF);
        guiGraphics.drawCenteredString(
            this.font,
            Component.translatable("screen.wpo_environmental_expansion.environmental_debug.subtitle"),
            centerX,
            y + 20,
            0xA8B0B8
        );

        if (noData) {
            guiGraphics.drawCenteredString(this.font, Component.literal("Waiting for data from the server..."), centerX, y + 96, 0xB0B0B0);
            guiGraphics.drawCenteredString(this.font, Component.literal("Press F6 or Esc to close."), centerX, y + panelH - 18, 0x8C949E);
            return;
        }

        int leftX = x + 14;
        int rightX = x + (panelW / 2) + 8;
        int headerY = y + 40;
        int leftY = headerY + 12;
        int rightY = headerY + 12;

        drawSectionHeader(guiGraphics, this.font, "Conditions", leftX, headerY, 176);
        drawSectionHeader(guiGraphics, this.font, "Context", rightX, headerY, 190);

        leftY = drawRow(guiGraphics, this.font, "Temp", String.format(Locale.ROOT, "%.1f C", packet.realTempC()), leftX, leftY, EnvDebugUi.tempColor(packet.realTempC()));
        leftY = drawRow(guiGraphics, this.font, "Humidity", String.format(Locale.ROOT, "%.0f%%", packet.realHumidityPct()), leftX, leftY, EnvDebugUi.humidityColor(packet.realHumidityPct()));
        leftY = drawRow(guiGraphics, this.font, "Wind", String.format(Locale.ROOT, "%.1f m/s", packet.realWindMs()), leftX, leftY, EnvDebugUi.windColor(packet.realWindMs()));
        leftY = drawRow(guiGraphics, this.font, "Precip ch", EnvDebugUi.formatPercent(packet.precipChancePct()), leftX, leftY, EnvDebugUi.chanceColor(packet.precipChancePct()));
        leftY = drawRow(guiGraphics, this.font, "Precip rate", String.format(Locale.ROOT, "%.1f mm/h", packet.precipMmHr()), leftX, leftY, EnvDebugUi.precipColor(packet.precipMmHr()));
        leftY = drawRow(guiGraphics, this.font, "Condense", EnvDebugUi.formatPercent(packet.condensationChancePct()), leftX, leftY, EnvDebugUi.chanceColor(packet.condensationChancePct()));
        leftY = drawRow(guiGraphics, this.font, "Freeze", EnvDebugUi.formatPercent(packet.freezingChancePct()), leftX, leftY, EnvDebugUi.chanceColor(packet.freezingChancePct()));
        leftY = drawRow(guiGraphics, this.font, "Thaw", EnvDebugUi.formatPercent(packet.thawChancePct()), leftX, leftY, EnvDebugUi.chanceColor(packet.thawChancePct()));
        leftY = drawRow(guiGraphics, this.font, "Crop boost", EnvDebugUi.formatPercent(packet.agricultureGrowthChancePct()), leftX, leftY, EnvDebugUi.chanceColor(packet.agricultureGrowthChancePct()));

        String weather = packet.isThundering() ? "Thunder" : packet.isRaining() ? "Rain" : "Clear";
        int weatherColor = packet.isThundering() ? 0xFFAA55 : packet.isRaining() ? 0x6FB7FF : 0xD8D8D8;
        rightY = drawRow(guiGraphics, this.font, "Time", EnvDebugUi.formatTime(packet.dayTime()), rightX, rightY, 0xD8D8D8);
        rightY = drawRow(guiGraphics, this.font, "Season", EnvDebugUi.formatSeason(packet.subSeasonIndex(), packet.tropicalPhaseIndex(), packet.tropicalCycle(), packet.seasonsEnabled()), rightX, rightY, 0xD8D8D8);
        rightY = drawRow(guiGraphics, this.font, "Weather", weather, rightX, rightY, weatherColor);
        rightY = drawRow(guiGraphics, this.font, "Biome", EnvDebugUi.shortenBiome(packet.biomeId()), rightX, rightY, 0xD8D8D8);
        rightY = drawRow(guiGraphics, this.font, "Archetype", EnvDebugUi.formatEnumName(packet.archetype()), rightX, rightY, 0x8EC7FF);
        rightY = drawRow(guiGraphics, this.font, "Target", EnvDebugUi.shortenBlock(packet.targetBlock()), rightX, rightY, 0xD8D8D8);
        rightY = drawRow(guiGraphics, this.font, "Location", EnvDebugUi.formatPos(packet.targetPos()), rightX, rightY, 0xB0B8C0);
        rightY = drawRow(guiGraphics, this.font, "Drought", packet.droughtActive() ? "ACTIVE" : "off", rightX, rightY, packet.droughtActive() ? 0xFF8844 : 0xAAB2BB);
        rightY = drawRow(guiGraphics, this.font, "Systems", EnvDebugUi.buildSystemsStr(packet), rightX, rightY, 0xD8D8D8);

        int bottomY = y + panelH - 42;
        guiGraphics.fill(x + 12, bottomY - 2, x + panelW - 12, bottomY - 1, 0xFF39424D);
        guiGraphics.drawString(this.font, Component.literal("Surface H2O: " + packet.surfaceWaterLevels()), x + 14, bottomY, packet.surfaceWaterLevels() > 0 ? 0x67B4FF : 0xAAB2BB, false);
        guiGraphics.drawString(this.font, Component.literal("Absorbed: " + packet.absorbedWater()), x + 118, bottomY, packet.absorbedWater() > 0 ? 0x7EDC7E : 0xAAB2BB, false);
        guiGraphics.drawString(this.font, Component.literal("Snow: " + packet.snowLayers()), x + 220, bottomY, packet.snowLayers() > 0 ? 0xEAEFFF : 0xAAB2BB, false);
        guiGraphics.drawString(this.font, Component.literal("Ice: " + packet.surfaceIceLevels()), x + 294, bottomY, packet.surfaceIceLevels() > 0 ? 0xB9D8FF : 0xAAB2BB, false);
        guiGraphics.drawString(this.font, Component.literal("Soil: " + EnvDebugUi.formatMoisture(packet.farmlandMoisture())), x + 346, bottomY, EnvDebugUi.moistureColor(packet.farmlandMoisture()), false);

        long ageMs = System.currentTimeMillis() - EnvDebugData.getLastReceiveTime();
        String ageText = ageMs > 0L ? (ageMs / 1000L) + "s old" : "fresh";
        int ageColor = ageMs > 5000L ? 0xFF6666 : 0xAAB2BB;
        guiGraphics.drawString(this.font, Component.literal("Age: " + ageText), x + panelW - 92, bottomY + 14, ageColor, false);

        guiGraphics.drawCenteredString(this.font, Component.literal("Press F6 or Esc to close."), centerX, y + panelH - 16, 0x8C949E);
        if (ageMs > 5000L) {
            guiGraphics.drawCenteredString(this.font, Component.literal("Data is stale; waiting for the next server sync."), centerX, y + panelH - 30, 0xFF6666);
        }
    }

    private static int drawRow(GuiGraphics guiGraphics, Font font, String label, String value, int x, int y, int valueColor) {
        guiGraphics.drawString(font, Component.literal(label + ":"), x, y, 0xA8B0B8, false);
        guiGraphics.drawString(font, Component.literal(value), x + 74, y, valueColor, false);
        return y + 12;
    }

    private static void drawSectionHeader(GuiGraphics guiGraphics, Font font, String title, int x, int y, int width) {
        guiGraphics.drawString(font, Component.literal(title), x, y, 0xFFFFFF, false);
        guiGraphics.fill(x, y + 10, x + width, y + 11, 0xFF39424D);
    }
}
