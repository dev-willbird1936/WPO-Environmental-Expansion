package net.skds.wpo.environmental;

/**
 * Provides seasonal modifiers for the environmental simulation.
 * These modifiers are applied based on the current sub-season (Early/Mid/Late of each season).
 */
public final class SeasonalModifiers {

    private SeasonalModifiers() {
    }

    public static double getRainChanceMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.2;
                case MID -> 1.35;
                case LATE -> 1.15;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 0.85;
                case MID -> 0.65;
                case LATE -> 0.75;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.95;
                case MID -> 1.1;
                case LATE -> 1.2;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.8;
                case MID -> 0.65;
                case LATE -> 0.55;
            };
        };
    }

    public static double getRainIntensityMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.15;
                case MID -> 1.25;
                case LATE -> 1.05;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 0.9;
                case MID -> 0.7;
                case LATE -> 0.8;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.95;
                case MID -> 1.1;
                case LATE -> 1.15;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.75;
                case MID -> 0.6;
                case LATE -> 0.5;
            };
        };
    }

    public static double getStormMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 0.85;
                case MID -> 0.95;
                case LATE -> 1.1;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 1.25;
                case MID -> 1.5;
                case LATE -> 1.35;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 1.0;
                case MID -> 1.15;
                case LATE -> 0.9;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.7;
                case MID -> 0.5;
                case LATE -> 0.4;
            };
        };
    }

    public static double getEvaporationMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 0.75;
                case MID -> 0.9;
                case LATE -> 1.1;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 1.25;
                case MID -> 1.5;
                case LATE -> 1.35;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 1.0;
                case MID -> 0.8;
                case LATE -> 0.65;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.5;
                case MID -> 0.4;
                case LATE -> 0.35;
            };
        };
    }

    public static double getSnowmeltMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.5;
                case MID -> 2.0;
                case LATE -> 1.75;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 1.25;
                case MID -> 1.0;
                case LATE -> 0.8;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.5;
                case MID -> 0.3;
                case LATE -> 0.2;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.15;
                case MID -> 0.1;
                case LATE -> 0.1;
            };
        };
    }

    public static double getReleaseMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.35;
                case MID -> 1.55;
                case LATE -> 1.25;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 0.9;
                case MID -> 0.7;
                case LATE -> 0.8;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.95;
                case MID -> 1.0;
                case LATE -> 1.1;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.7;
                case MID -> 0.5;
                case LATE -> 0.4;
            };
        };
    }

    public static double getAbsorptionMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.25;
                case MID -> 1.35;
                case LATE -> 1.15;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 0.85;
                case MID -> 0.7;
                case LATE -> 0.75;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.95;
                case MID -> 1.05;
                case LATE -> 1.15;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.9;
                case MID -> 0.8;
                case LATE -> 0.75;
            };
        };
    }

    public static double getRetentionMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.2;
                case MID -> 1.3;
                case LATE -> 1.1;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 0.85;
                case MID -> 0.7;
                case LATE -> 0.75;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.95;
                case MID -> 1.1;
                case LATE -> 1.2;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 1.05;
                case MID -> 1.15;
                case LATE -> 1.2;
            };
        };
    }

    public static double getCollectorMultiplier(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 1.25;
                case MID -> 1.4;
                case LATE -> 1.15;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 0.85;
                case MID -> 0.65;
                case LATE -> 0.75;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 0.9;
                case MID -> 1.05;
                case LATE -> 1.2;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.75;
                case MID -> 0.6;
                case LATE -> 0.5;
            };
        };
    }

    public static double getDroughtSensitivity(SeasonManager.SubSeason subSeason) {
        return switch (subSeason.season()) {
            case SPRING -> switch (subSeason.phase()) {
                case EARLY -> 0.85;
                case MID -> 0.7;
                case LATE -> 0.9;
            };
            case SUMMER -> switch (subSeason.phase()) {
                case EARLY -> 1.2;
                case MID -> 1.4;
                case LATE -> 1.3;
            };
            case AUTUMN -> switch (subSeason.phase()) {
                case EARLY -> 1.1;
                case MID -> 0.95;
                case LATE -> 0.8;
            };
            case WINTER -> switch (subSeason.phase()) {
                case EARLY -> 0.65;
                case MID -> 0.5;
                case LATE -> 0.45;
            };
        };
    }

    public static double getTropicalWetMultiplier(SeasonManager.TropicalPhase phase) {
        return phase == SeasonManager.TropicalPhase.WET ? 1.25 : 0.75;
    }

    public static double getTropicalDryMultiplier(SeasonManager.TropicalPhase phase) {
        return phase == SeasonManager.TropicalPhase.DRY ? 1.35 : 0.65;
    }
}
