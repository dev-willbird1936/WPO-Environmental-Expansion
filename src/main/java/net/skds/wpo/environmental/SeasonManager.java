package net.skds.wpo.environmental;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Manages the seasonal cycle for the environmental system.
 * Supports both temperate (4 seasons) and tropical (wet/dry) cycles.
 */
public final class SeasonManager {

    private SeasonManager() {
    }

    public enum Season {
        SPRING,
        SUMMER,
        AUTUMN,
        WINTER
    }

    public enum SeasonPhase {
        EARLY,
        MID,
        LATE
    }

    public enum TropicalPhase {
        WET,
        DRY
    }

    public record SubSeason(Season season, SeasonPhase phase) {
        public static final SubSeason EARLY_SPRING = new SubSeason(Season.SPRING, SeasonPhase.EARLY);
        public static final SubSeason MID_SPRING = new SubSeason(Season.SPRING, SeasonPhase.MID);
        public static final SubSeason LATE_SPRING = new SubSeason(Season.SPRING, SeasonPhase.LATE);
        public static final SubSeason EARLY_SUMMER = new SubSeason(Season.SUMMER, SeasonPhase.EARLY);
        public static final SubSeason MID_SUMMER = new SubSeason(Season.SUMMER, SeasonPhase.MID);
        public static final SubSeason LATE_SUMMER = new SubSeason(Season.SUMMER, SeasonPhase.LATE);
        public static final SubSeason EARLY_AUTUMN = new SubSeason(Season.AUTUMN, SeasonPhase.EARLY);
        public static final SubSeason MID_AUTUMN = new SubSeason(Season.AUTUMN, SeasonPhase.MID);
        public static final SubSeason LATE_AUTUMN = new SubSeason(Season.AUTUMN, SeasonPhase.LATE);
        public static final SubSeason EARLY_WINTER = new SubSeason(Season.WINTER, SeasonPhase.EARLY);
        public static final SubSeason MID_WINTER = new SubSeason(Season.WINTER, SeasonPhase.MID);
        public static final SubSeason LATE_WINTER = new SubSeason(Season.WINTER, SeasonPhase.LATE);

        private static final SubSeason[] TEMPERATE_SEASONS = {
            EARLY_SPRING, MID_SPRING, LATE_SPRING,
            EARLY_SUMMER, MID_SUMMER, LATE_SUMMER,
            EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN,
            EARLY_WINTER, MID_WINTER, LATE_WINTER
        };

        public static SubSeason fromIndex(int index) {
            return TEMPERATE_SEASONS[Mth.positiveModulo(index, 12)];
        }

        public int index() {
            return switch (this.season()) {
                case SPRING -> switch (this.phase()) {
                    case EARLY -> 0;
                    case MID -> 1;
                    case LATE -> 2;
                };
                case SUMMER -> switch (this.phase()) {
                    case EARLY -> 3;
                    case MID -> 4;
                    case LATE -> 5;
                };
                case AUTUMN -> switch (this.phase()) {
                    case EARLY -> 6;
                    case MID -> 7;
                    case LATE -> 8;
                };
                case WINTER -> switch (this.phase()) {
                    case EARLY -> 9;
                    case MID -> 10;
                    case LATE -> 11;
                };
            };
        }
    }

    public static boolean isSeasonsEnabled() {
        return EnvironmentalConfig.COMMON.seasons.get();
    }

    public static boolean isTropicalCycle() {
        return EnvironmentalConfig.COMMON.tropicalSeasons.get();
    }

    public static long getWorldDay(ServerLevel level) {
        return level.getDayTime() / 24000L;
    }

    public static int getPhaseLengthDays() {
        return Math.max(1, EnvironmentalConfig.COMMON.seasonPhaseLengthDays.get());
    }

    public static int getSeasonLengthDays() {
        return getPhaseLengthDays() * 3;
    }

    public static SubSeason getSubSeason(ServerLevel level) {
        if (!isSeasonsEnabled()) {
            return SubSeason.MID_SPRING;
        }
        long day = getWorldDay(level);
        int phaseLength = getPhaseLengthDays();
        int subSeasonIndex = (int) (day / phaseLength);
        
        if (isTropicalCycle()) {
            return getTropicalSubSeason(day, phaseLength);
        }
        
        return SubSeason.fromIndex(subSeasonIndex);
    }

    public static TropicalPhase getTropicalPhase(ServerLevel level) {
        if (!isSeasonsEnabled() || !isTropicalCycle()) {
            return TropicalPhase.WET;
        }
        long day = getWorldDay(level);
        int phaseLength = getPhaseLengthDays();
        return getTropicalPhase(day, phaseLength);
    }

    private static TropicalPhase getTropicalPhase(long day, int phaseLength) {
        int cyclePosition = (int) ((day / phaseLength) % 4);
        return cyclePosition < 2 ? TropicalPhase.WET : TropicalPhase.DRY;
    }

    private static SubSeason getTropicalSubSeason(long day, int phaseLength) {
        TropicalPhase phase = getTropicalPhase(day, phaseLength);
        int subIndex = (int) ((day / phaseLength) % 4);
        
        if (phase == TropicalPhase.WET) {
            return subIndex < 2 ? SubSeason.MID_SPRING : SubSeason.LATE_SPRING;
        } else {
            return subIndex < 2 ? SubSeason.EARLY_SUMMER : SubSeason.MID_SUMMER;
        }
    }

    public static double getSeasonProgress(ServerLevel level) {
        if (!isSeasonsEnabled()) {
            return 0.5;
        }
        long day = getWorldDay(level);
        int phaseLength = getPhaseLengthDays();
        int dayInPhase = (int) (day % phaseLength);
        return dayInPhase / (double) phaseLength;
    }

    public static double getTransitionFactor(ServerLevel level) {
        double progress = getSeasonProgress(level);
        if (progress < 0.33) {
            return progress * 3.0;
        } else if (progress > 0.66) {
            return (1.0 - progress) * 3.0;
        }
        return 1.0;
    }

    public static double getSeasonTemperatureModifier(ServerLevel level) {
        if (!isSeasonsEnabled()) {
            return 0.0;
        }
        SubSeason subSeason = getSubSeason(level);
        double baseTemp;
        
        switch (subSeason.season()) {
            case WINTER -> baseTemp = -0.8;
            case SPRING -> baseTemp = -0.1;
            case AUTUMN -> baseTemp = 0.1;
            case SUMMER -> baseTemp = 0.8;
            default -> baseTemp = 0.0;
        }
        
        double transitionFactor = getTransitionFactor(level);
        return baseTemp * transitionFactor * 0.3;
    }
}
