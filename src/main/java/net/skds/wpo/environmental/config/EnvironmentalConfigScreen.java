package net.skds.wpo.environmental.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.skds.wpo.environmental.EnvironmentalConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class EnvironmentalConfigScreen extends Screen {
    private static final int LABEL_X_OFFSET = 180;
    private static final int CONTROL_X_OFFSET = 18;
    private static final int CONTROL_WIDTH = 142;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 94;
    private static final int LIST_BOTTOM_PADDING = 78;

    private final Screen parent;
    private final List<ToggleOption> toggleOptions = new ArrayList<>();
    private final List<IntOption> intOptions = new ArrayList<>();
    private final List<DoubleOption> doubleOptions = new ArrayList<>();

    private Page page = Page.SYSTEMS;
    private Button systemsTabButton;
    private Button tuningTabButton;
    private Component statusMessage = CommonComponents.EMPTY;
    private int systemsScroll;
    private int tuningScroll;

    public EnvironmentalConfigScreen(Screen parent) {
        super(Component.literal("WPO: Environmental Expansion Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        toggleOptions.clear();
        intOptions.clear();
        doubleOptions.clear();
        clearWidgets();

        int centerX = this.width / 2;
        int top = LIST_TOP;

        systemsTabButton = this.addRenderableWidget(Button.builder(Component.literal("Systems"), button -> switchPage(Page.SYSTEMS))
            .bounds(centerX - 102, 34, 100, 20)
            .build());
        tuningTabButton = this.addRenderableWidget(Button.builder(Component.literal("Tuning"), button -> switchPage(Page.TUNING))
            .bounds(centerX + 2, 34, 100, 20)
            .build());

        int row = 0;
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Rain Accumulation", EnvironmentalConfig.COMMON.rainAccumulation.get(), EnvironmentalConfig.COMMON.rainAccumulation::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Puddles", EnvironmentalConfig.COMMON.puddles.get(), EnvironmentalConfig.COMMON.puddles::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Distant Rain Catch-up", EnvironmentalConfig.COMMON.distantRainCatchup.get(), EnvironmentalConfig.COMMON.distantRainCatchup::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Evaporation", EnvironmentalConfig.COMMON.evaporation.get(), EnvironmentalConfig.COMMON.evaporation::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Droughts", EnvironmentalConfig.COMMON.droughts.get(), EnvironmentalConfig.COMMON.droughts::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Floods", EnvironmentalConfig.COMMON.floods.get(), EnvironmentalConfig.COMMON.floods::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Snowmelt", EnvironmentalConfig.COMMON.snowmelt.get(), EnvironmentalConfig.COMMON.snowmelt::set);
        addToggle(top + row++ * ROW_HEIGHT, Page.SYSTEMS, "Absorption", EnvironmentalConfig.COMMON.absorption.get(), EnvironmentalConfig.COMMON.absorption::set);
        addToggle(top + row * ROW_HEIGHT, Page.SYSTEMS, "Seasons", EnvironmentalConfig.COMMON.seasons.get(), EnvironmentalConfig.COMMON.seasons::set);

        row = 0;
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Update Interval", EnvironmentalConfig.COMMON.updateInterval.get(), 1, 40, EnvironmentalConfig.COMMON.updateInterval::set);
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Sample Radius", EnvironmentalConfig.COMMON.sampleRadius.get(), 4, 96, EnvironmentalConfig.COMMON.sampleRadius::set);
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Checks Per Player", EnvironmentalConfig.COMMON.columnChecksPerPlayer.get(), 1, 64, EnvironmentalConfig.COMMON.columnChecksPerPlayer::set);
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Arrival Checks", EnvironmentalConfig.COMMON.arrivalColumnChecks.get(), 0, 256, EnvironmentalConfig.COMMON.arrivalColumnChecks::set);
        addDoubleField(top + row++ * ROW_HEIGHT, Page.TUNING, "Rain Intensity", EnvironmentalConfig.COMMON.rainIntensity.get(), 0.0D, 8.0D, EnvironmentalConfig.COMMON.rainIntensity::set);
        addDoubleField(top + row++ * ROW_HEIGHT, Page.TUNING, "Storm Intensity", EnvironmentalConfig.COMMON.stormIntensity.get(), 1.0D, 12.0D, EnvironmentalConfig.COMMON.stormIntensity::set);
        addDoubleField(top + row++ * ROW_HEIGHT, Page.TUNING, "Collector Efficiency", EnvironmentalConfig.COMMON.collectorEfficiency.get(), 0.0D, 8.0D, EnvironmentalConfig.COMMON.collectorEfficiency::set);
        addDoubleField(top + row++ * ROW_HEIGHT, Page.TUNING, "Evaporation Chance", EnvironmentalConfig.COMMON.evaporationChance.get(), 0.0D, 4.0D, EnvironmentalConfig.COMMON.evaporationChance::set);
        addDoubleField(top + row++ * ROW_HEIGHT, Page.TUNING, "Absorption Chance", EnvironmentalConfig.COMMON.absorptionChance.get(), 0.0D, 4.0D, EnvironmentalConfig.COMMON.absorptionChance::set);
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Ambient Wetness Cap", EnvironmentalConfig.COMMON.ambientWetnessCap.get(), 0, 200_000, EnvironmentalConfig.COMMON.ambientWetnessCap::set);
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Ambient Rain Gain", EnvironmentalConfig.COMMON.ambientWetnessRainGain.get(), 0, 1000, EnvironmentalConfig.COMMON.ambientWetnessRainGain::set);
        addIntField(top + row++ * ROW_HEIGHT, Page.TUNING, "Ambient Dry Decay", EnvironmentalConfig.COMMON.ambientWetnessDryDecay.get(), 0, 1000, EnvironmentalConfig.COMMON.ambientWetnessDryDecay::set);
        addIntField(top + row * ROW_HEIGHT, Page.TUNING, "Ambient Max Levels", EnvironmentalConfig.COMMON.ambientMaxPuddleLevels.get(), 0, 8, EnvironmentalConfig.COMMON.ambientMaxPuddleLevels::set);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveAndClose())
            .bounds(centerX - 154, this.height - 28, 150, 20)
            .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
            .bounds(centerX + 4, this.height - 28, 150, 20)
            .build());

        updatePageState();
    }

    private void addToggle(int y, Page page, String label, boolean initialValue, Consumer<Boolean> setter) {
        int centerX = this.width / 2;
        CycleButton<Boolean> button = this.addRenderableWidget(CycleButton.booleanBuilder(CommonComponents.OPTION_ON, CommonComponents.OPTION_OFF)
            .withInitialValue(initialValue)
            .displayOnlyValue()
            .create(centerX + CONTROL_X_OFFSET, y, CONTROL_WIDTH, 20, CommonComponents.EMPTY));
        toggleOptions.add(new ToggleOption(Component.literal(label), button, setter, y, page));
    }

    private void addIntField(int y, Page page, String label, int initialValue, int min, int max, IntConsumer setter) {
        int centerX = this.width / 2;
        EditBox box = new EditBox(this.font, centerX + CONTROL_X_OFFSET, y, CONTROL_WIDTH, 20, Component.literal(label));
        box.setValue(String.valueOf(initialValue));
        this.addRenderableWidget(box);
        intOptions.add(new IntOption(Component.literal(label), box, setter, min, max, y, page));
    }

    private void addDoubleField(int y, Page page, String label, double initialValue, double min, double max, Consumer<Double> setter) {
        int centerX = this.width / 2;
        EditBox box = new EditBox(this.font, centerX + CONTROL_X_OFFSET, y, CONTROL_WIDTH, 20, Component.literal(label));
        box.setValue(trimDouble(initialValue));
        this.addRenderableWidget(box);
        doubleOptions.add(new DoubleOption(Component.literal(label), box, setter, min, max, y, page));
    }

    private void switchPage(Page page) {
        this.page = page;
        updatePageState();
    }

    private void updatePageState() {
        clampScroll();
        systemsTabButton.active = page != Page.SYSTEMS;
        tuningTabButton.active = page != Page.TUNING;
        int scroll = getScroll(page);
        int viewportTop = LIST_TOP;
        int viewportBottom = this.height - LIST_BOTTOM_PADDING;
        toggleOptions.forEach(option -> option.updateLayout(page, scroll, viewportTop, viewportBottom));
        intOptions.forEach(option -> option.updateLayout(page, scroll, viewportTop, viewportBottom));
        doubleOptions.forEach(option -> option.updateLayout(page, scroll, viewportTop, viewportBottom));
    }

    private void clampScroll() {
        systemsScroll = Mth.clamp(systemsScroll, 0, getMaxScroll(Page.SYSTEMS));
        tuningScroll = Mth.clamp(tuningScroll, 0, getMaxScroll(Page.TUNING));
    }

    private int getScroll(Page page) {
        return page == Page.SYSTEMS ? systemsScroll : tuningScroll;
    }

    private void setScroll(Page page, int value) {
        if (page == Page.SYSTEMS) {
            systemsScroll = value;
        } else {
            tuningScroll = value;
        }
    }

    private int getMaxScroll(Page page) {
        int contentBottom = 0;
        for (ToggleOption option : toggleOptions) {
            if (option.page() == page) {
                contentBottom = Math.max(contentBottom, option.baseY() + 20);
            }
        }
        for (IntOption option : intOptions) {
            if (option.page() == page) {
                contentBottom = Math.max(contentBottom, option.baseY() + 20);
            }
        }
        for (DoubleOption option : doubleOptions) {
            if (option.page() == page) {
                contentBottom = Math.max(contentBottom, option.baseY() + 20);
            }
        }
        int viewportHeight = this.height - LIST_BOTTOM_PADDING - LIST_TOP;
        return Math.max(0, contentBottom - LIST_TOP - viewportHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = getMaxScroll(page);
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int step = ROW_HEIGHT;
        int next = Mth.clamp(getScroll(page) - (int) Math.signum(delta) * step, 0, maxScroll);
        if (next != getScroll(page)) {
            setScroll(page, next);
            updatePageState();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        try {
            for (ToggleOption option : toggleOptions) {
                option.setter.accept(option.button.getValue());
            }
            for (IntOption option : intOptions) {
                option.setter.accept(parseInt(option.box.getValue(), option.min, option.max));
            }
            for (DoubleOption option : doubleOptions) {
                option.setter.accept(parseDouble(option.box.getValue(), option.min, option.max));
            }
            EnvironmentalConfig.save();
            this.minecraft.setScreen(parent);
        } catch (NumberFormatException ex) {
            statusMessage = Component.literal("Enter valid numbers for every field.");
        }
    }

    private static int parseInt(String text, int min, int max) {
        return Mth.clamp(Integer.parseInt(text.trim()), min, max);
    }

    private static double parseDouble(String text, double min, double max) {
        return Mth.clamp(Double.parseDouble(text.trim()), min, max);
    }

    private static String trimDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int labelX = centerX - LABEL_X_OFFSET;

        guiGraphics.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, page.title, centerX, 66, 0xE0E0E0);

        for (ToggleOption option : toggleOptions) {
            if (option.isVisible()) {
                guiGraphics.drawString(this.font, option.label(), labelX, option.button().getY() + (20 - this.font.lineHeight) / 2, 0xFFFFFF, false);
            }
        }
        for (IntOption option : intOptions) {
            if (option.isVisible()) {
                guiGraphics.drawString(this.font, option.label(), labelX, option.box().getY() + (20 - this.font.lineHeight) / 2, 0xFFFFFF, false);
            }
        }
        for (DoubleOption option : doubleOptions) {
            if (option.isVisible()) {
                guiGraphics.drawString(this.font, option.label(), labelX, option.box().getY() + (20 - this.font.lineHeight) / 2, 0xFFFFFF, false);
            }
        }

        guiGraphics.drawCenteredString(this.font,
            Component.literal("For extended tuning beyond this screen, edit the TOML directly."),
            centerX, this.height - 52, 0xC0C0C0);

        int maxScroll = getMaxScroll(page);
        if (maxScroll > 0) {
            guiGraphics.drawCenteredString(this.font,
                Component.literal("Mouse wheel to scroll"),
                centerX, this.height - 64, 0xA0A0A0);
        }

        if (!statusMessage.getString().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, statusMessage, centerX, this.height - 40, 0xFF8080);
        }
    }

    private enum Page {
        SYSTEMS(Component.literal("Systems")),
        TUNING(Component.literal("Tuning"));

        private final Component title;

        Page(Component title) {
            this.title = title;
        }
    }

    private interface PagedOption {
        void updateLayout(Page page, int scroll, int viewportTop, int viewportBottom);
    }

    private record ToggleOption(Component label, CycleButton<Boolean> button, Consumer<Boolean> setter, int baseY, Page page) implements PagedOption {
        @Override
        public void updateLayout(Page currentPage, int scroll, int viewportTop, int viewportBottom) {
            int y = baseY - scroll;
            button.setY(y);
            boolean visible = this.page == currentPage && y + 20 > viewportTop && y < viewportBottom;
            button.visible = visible;
            button.active = visible;
        }

        public boolean isVisible() {
            return button.visible;
        }
    }

    private record IntOption(Component label, EditBox box, IntConsumer setter, int min, int max, int baseY, Page page) implements PagedOption {
        @Override
        public void updateLayout(Page currentPage, int scroll, int viewportTop, int viewportBottom) {
            int y = baseY - scroll;
            box.setY(y);
            boolean visible = this.page == currentPage && y + 20 > viewportTop && y < viewportBottom;
            box.visible = visible;
            box.setEditable(visible);
            box.active = visible;
        }

        public boolean isVisible() {
            return box.visible;
        }
    }

    private record DoubleOption(Component label, EditBox box, Consumer<Double> setter, double min, double max, int baseY, Page page) implements PagedOption {
        @Override
        public void updateLayout(Page currentPage, int scroll, int viewportTop, int viewportBottom) {
            int y = baseY - scroll;
            box.setY(y);
            boolean visible = this.page == currentPage && y + 20 > viewportTop && y < viewportBottom;
            box.visible = visible;
            box.setEditable(visible);
            box.active = visible;
        }

        public boolean isVisible() {
            return box.visible;
        }
    }
}
