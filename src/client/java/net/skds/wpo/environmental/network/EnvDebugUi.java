package net.skds.wpo.environmental.network;

import net.minecraft.core.BlockPos;

import java.util.Locale;

final class EnvDebugUi {

    private EnvDebugUi() {
    }

    static String formatSeason(int subSeasonIndex, int tropicalPhase, boolean tropical, boolean seasonsEnabled) {
        if (!seasonsEnabled) {
            return "Disabled";
        }
        if (tropical) {
            return tropicalPhase == 0 ? "Wet Season" : "Dry Season";
        }
        String[] names = {
            "Early Spring", "Mid Spring", "Late Spring",
            "Early Summer", "Mid Summer", "Late Summer",
            "Early Autumn", "Mid Autumn", "Late Autumn",
            "Early Winter", "Mid Winter", "Late Winter"
        };
        return names[Math.floorMod(subSeasonIndex, 12)];
    }

    static String formatTime(long dayTime) {
        long ticks = Math.floorMod(dayTime, 24000L);
        int hours = (int) (ticks / 1000L);
        int minutes = (int) (((ticks % 1000L) / 1000.0D) * 60.0D);
        String ampm = hours >= 12 ? "PM" : "AM";
        int displayHour = hours % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }
        return String.format(Locale.ROOT, "%d:%02d %s", displayHour, minutes, ampm);
    }

    static String shortenBiome(String biomeId) {
        return limit(prettifyIdentifier(biomeId, "???"), 30);
    }

    static String shortenBlock(String blockId) {
        return limit(prettifyIdentifier(blockId, "(none)"), 28);
    }

    static String formatEnumName(String value) {
        return prettifyIdentifier(value, "???");
    }

    static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "(none)";
        }
        return String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    static String formatMult(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static int multColor(double v) {
        if (v > 1.3D) return 0xFF6666;
        if (v > 1.1D) return 0xFFAA55;
        if (v < 0.7D) return 0x6666FF;
        if (v < 0.9D) return 0x55AAFF;
        return 0xE0E0E0;
    }

    static int tempColor(float tempC) {
        if (tempC > 40.0F) return 0xFF4444;
        if (tempC > 32.0F) return 0xFF8844;
        if (tempC > 24.0F) return 0xFFAA44;
        if (tempC > 15.0F) return 0xE0E0E0;
        if (tempC > 5.0F) return 0x55AAFF;
        if (tempC > -5.0F) return 0x6688FF;
        return 0x6666FF;
    }

    static int humidityColor(float humidity) {
        if (humidity > 90.0F) return 0x4488FF;
        if (humidity > 70.0F) return 0x55AAFF;
        if (humidity > 50.0F) return 0x77DD77;
        if (humidity > 30.0F) return 0xFFAA55;
        return 0xFF8844;
    }

    static int windColor(float windMs) {
        if (windMs > 20.0F) return 0xFF6666;
        if (windMs > 12.0F) return 0xFFAA66;
        if (windMs > 6.0F) return 0xE0E0E0;
        if (windMs > 2.0F) return 0x77DD77;
        return 0x55AAFF;
    }

    static String formatPercent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    static int chanceColor(float chancePct) {
        if (chancePct > 80.0F) return 0xFF6666;
        if (chancePct > 55.0F) return 0xFFAA55;
        if (chancePct > 25.0F) return 0xE0E0E0;
        if (chancePct > 5.0F) return 0x77DD77;
        return 0xAAB2BB;
    }

    static int precipColor(float precipMmHr) {
        if (precipMmHr > 10.0F) return 0xFF6666;
        if (precipMmHr > 4.0F) return 0xFFAA55;
        if (precipMmHr > 1.0F) return 0x6FB7FF;
        if (precipMmHr > 0.2F) return 0x55AAFF;
        return 0xAAB2BB;
    }

    static String formatMoisture(int moisture) {
        return moisture < 0 ? "n/a" : moisture + "/7";
    }

    static int moistureColor(int moisture) {
        if (moisture < 0) return 0x909090;
        if (moisture >= 7) return 0x77DD77;
        if (moisture >= 4) return 0xCFE88B;
        if (moisture >= 1) return 0xFFAA55;
        return 0xFF6666;
    }

    static String buildSystemsStr(EnvDebugPacket d) {
        StringBuilder sb = new StringBuilder();
        appendSystem(sb, d.absorptionEnabled(), "Abs");
        appendSystem(sb, d.evaporationEnabled(), "Eva");
        appendSystem(sb, d.snowmeltEnabled(), "Snw");
        appendSystem(sb, d.condensationEnabled(), "Con");
        appendSystem(sb, d.surfaceIceEnabled(), "Ice");
        appendSystem(sb, d.agricultureEnabled(), "Agr");
        appendSystem(sb, d.floodsEnabled(), "Fld");
        appendSystem(sb, d.distantRainCatchupEnabled(), "DRC");
        String s = sb.toString();
        return s.isEmpty() ? "all off" : s;
    }

    private static void appendSystem(StringBuilder sb, boolean enabled, String token) {
        if (!enabled) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(token);
    }

    private static String prettifyIdentifier(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String text = value;
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon < text.length() - 1) {
            text = text.substring(colon + 1);
        }

        text = text.replace('-', ' ').replace('_', ' ');
        text = text.replaceAll("([a-z])([A-Z])", "$1 $2");
        text = text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return fallback;
        }

        StringBuilder out = new StringBuilder(text.length());
        for (String part : text.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.length() == 0 ? fallback : out.toString();
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
