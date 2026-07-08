package net.skds.wpo.environmental.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.Properties;

final class EnvDebugOverlayStack {
    private static final String FRAME_KEY = "compact_debug_overlay_stack.frame";
    private static final String LEFT_NEXT_Y_KEY = "compact_debug_overlay_stack.left.next_y";
    private static final String RIGHT_NEXT_Y_KEY = "compact_debug_overlay_stack.right.next_y";
    private static final int TOP_PADDING = 2;
    private static final int OVERLAY_GAP = 2;

    private EnvDebugOverlayStack() {
    }

    static int reserveLeft(final Minecraft minecraft, final Font font, final int baseLineOffset, final int lineGap, final int lineCount) {
        final int baseY = baseY(font, baseLineOffset, lineGap);
        final int blockHeight = blockHeight(font, lineGap, lineCount);
        synchronized (System.getProperties()) {
            final Properties properties = System.getProperties();
            resetIfNewFrame(minecraft, properties, baseY);

            final int y = Math.max(baseY, getInt(properties, LEFT_NEXT_Y_KEY, baseY));
            properties.setProperty(LEFT_NEXT_Y_KEY, Integer.toString(y + blockHeight + OVERLAY_GAP));
            return y;
        }
    }

    static int reserveRight(final Minecraft minecraft, final Font font, final int baseLineOffset, final int lineGap, final int lineCount) {
        final int baseY = baseY(font, baseLineOffset, lineGap);
        final int blockHeight = blockHeight(font, lineGap, lineCount);
        synchronized (System.getProperties()) {
            final Properties properties = System.getProperties();
            resetIfNewFrame(minecraft, properties, baseY);

            final int y = Math.max(baseY, getInt(properties, RIGHT_NEXT_Y_KEY, baseY));
            properties.setProperty(RIGHT_NEXT_Y_KEY, Integer.toString(y + blockHeight + OVERLAY_GAP));
            return y;
        }
    }

    static int reserveBoth(final Minecraft minecraft, final Font font, final int baseLineOffset, final int lineGap, final int leftLineCount, final int rightLineCount) {
        final int baseY = baseY(font, baseLineOffset, lineGap);
        final int leftHeight = blockHeight(font, lineGap, leftLineCount);
        final int rightHeight = blockHeight(font, lineGap, rightLineCount);
        synchronized (System.getProperties()) {
            final Properties properties = System.getProperties();
            resetIfNewFrame(minecraft, properties, baseY);

            final int y = Math.max(
                baseY,
                Math.max(
                    getInt(properties, LEFT_NEXT_Y_KEY, baseY),
                    getInt(properties, RIGHT_NEXT_Y_KEY, baseY)
                )
            );
            properties.setProperty(LEFT_NEXT_Y_KEY, Integer.toString(y + leftHeight + OVERLAY_GAP));
            properties.setProperty(RIGHT_NEXT_Y_KEY, Integer.toString(y + rightHeight + OVERLAY_GAP));
            return y;
        }
    }

    private static void resetIfNewFrame(final Minecraft minecraft, final Properties properties, final int baseY) {
        final String frameToken = frameToken(minecraft);
        if (!frameToken.equals(properties.getProperty(FRAME_KEY))) {
            properties.setProperty(FRAME_KEY, frameToken);
            properties.setProperty(LEFT_NEXT_Y_KEY, Integer.toString(baseY));
            properties.setProperty(RIGHT_NEXT_Y_KEY, Integer.toString(baseY));
        }
    }

    private static String frameToken(final Minecraft minecraft) {
        return minecraft.getWindow().getGuiScaledWidth()
            + "x"
            + minecraft.getWindow().getGuiScaledHeight()
            + ":"
            + minecraft.getFrameTimeNs();
    }

    private static int baseY(final Font font, final int baseLineOffset, final int lineGap) {
        return TOP_PADDING + ((font.lineHeight + lineGap) * baseLineOffset);
    }

    private static int blockHeight(final Font font, final int lineGap, final int lineCount) {
        return Math.max(0, lineCount * (font.lineHeight + lineGap));
    }

    private static int getInt(final Properties properties, final String key, final int fallback) {
        final String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }
}
