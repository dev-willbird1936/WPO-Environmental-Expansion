package net.skds.wpo.environmental;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Provides day/night cycle modifiers for the environmental simulation.
 * Different biomes experience different day/night temperature swings.
 */
public final class DayNightModifiers {

    private DayNightModifiers() {
    }

    public enum TimeOfDay {
        DAWN,
        DAY,
        DUSK,
        NIGHT
    }

    public static TimeOfDay getTimeOfDay(ServerLevel level) {
        long timeOfDay = level.getDayTime() % 24000L;
        if (timeOfDay < 1000) {
            return TimeOfDay.DAWN;
        } else if (timeOfDay < 11000) {
            return TimeOfDay.DAY;
        } else if (timeOfDay < 13000) {
            return TimeOfDay.DUSK;
        } else {
            return TimeOfDay.NIGHT;
        }
    }

    public static double getTimeOfDayProgress(ServerLevel level) {
        long timeOfDay = level.getDayTime() % 24000L;
        return timeOfDay / 24000.0;
    }

    public static double getSunAngle(ServerLevel level) {
        long timeOfDay = level.getDayTime() % 24000L;
        double angle = (timeOfDay - 6000) / 12000.0 * Math.PI;
        return Mth.clamp(Mth.sin((float) angle), -1.0F, 1.0F);
    }

    public static double getEvaporationMultiplier(ServerLevel level, BiomeEnvironmentProfile profile) {
        double base = 1.0;
        
        switch (getTimeOfDay(level)) {
            case DAWN -> base = 0.4;
            case DAY -> {
                double sunAngle = getSunAngle(level);
                if (sunAngle > 0) {
                    base = 0.5 + (sunAngle * 1.0);
                } else {
                    base = 0.5;
                }
            }
            case DUSK -> base = 0.6;
            case NIGHT -> base = 0.25;
        }

        double biomeTemp = profile.rainIntensityMultiplier() * 2.0 - 1.0;
        if (biomeTemp > 0.5) {
            base *= 1.0 + (biomeTemp * 0.8);
        } else if (biomeTemp < -0.3) {
            base *= 1.0 + (biomeTemp * 0.5);
        }

        return Mth.clamp(base, 0.1, 2.5);
    }

    public static double getSnowmeltMultiplier(ServerLevel level, BiomeEnvironmentProfile profile) {
        double sunAngle = getSunAngle(level);
        
        if (sunAngle <= 0) {
            return 0.15;
        }

        double base = 0.3 + (sunAngle * 1.2);
        
        double biomeTemp = profile.rainIntensityMultiplier() * 2.0 - 1.0;
        if (biomeTemp > 0.3) {
            base *= 1.0 + (biomeTemp * 0.6);
        } else if (biomeTemp < -0.5) {
            base *= 0.6;
        }

        return Mth.clamp(base, 0.1, 2.0);
    }

    public static double getRainChanceMultiplier(ServerLevel level) {
        return switch (getTimeOfDay(level)) {
            case DAWN -> 1.15;
            case DAY -> 1.0;
            case DUSK -> 1.2;
            case NIGHT -> 0.85;
        };
    }

    public static double getCollectorMultiplier(ServerLevel level, BiomeEnvironmentProfile profile) {
        double sunAngle = getSunAngle(level);
        
        if (sunAngle <= 0) {
            return 0.4;
        }

        double base = 0.5 + (sunAngle * 0.8);
        
        double biomeTemp = profile.rainIntensityMultiplier() * 2.0 - 1.0;
        if (biomeTemp > 0.4) {
            base *= 1.0 + (biomeTemp * 0.4);
        }

        return Mth.clamp(base, 0.3, 1.8);
    }

    public static double getReleaseMultiplier(ServerLevel level) {
        double sunAngle = getSunAngle(level);
        
        if (sunAngle <= 0) {
            return 0.5;
        }

        return 0.6 + (sunAngle * 0.8);
    }

    public static double getAbsorptionMultiplier(ServerLevel level) {
        return switch (getTimeOfDay(level)) {
            case DAWN -> 1.1;
            case DAY -> 1.0;
            case DUSK -> 1.15;
            case NIGHT -> 1.25;
        };
    }

    public static double getTemperatureModifier(ServerLevel level, BiomeEnvironmentProfile profile) {
        double sunAngle = getSunAngle(level);
        
        double biomeTemp = profile.rainIntensityMultiplier() * 2.0 - 1.0;
        
        return sunAngle * (0.5 + biomeTemp * 0.5);
    }
}
