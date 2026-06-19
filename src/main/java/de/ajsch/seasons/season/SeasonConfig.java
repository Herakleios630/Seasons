package de.ajsch.seasons.season;

import de.ajsch.seasons.config.ConfigManager;

public class SeasonConfig {

    private final ConfigManager config;

    public SeasonConfig(ConfigManager config) {
        this.config = config;
    }

    public int getYearLengthDays() {
        return getDaysForSeason(Season.SPRING) + getDaysForSeason(Season.SUMMER)
            + getDaysForSeason(Season.FALL) + getDaysForSeason(Season.WINTER);
    }

    public int getDaysForSeason(Season season) {
        int base = switch (season) {
            case SPRING -> config.getSpringDays();
            case SUMMER -> config.getSummerDays();
            case FALL -> config.getFallDays();
            case WINTER -> config.getWinterDays();
        };
        if (config.isDebugMode()) {
            // Im Debug-Mode: alle Seasons gleich lang (Jahr = 20 Tage)
            int total = 4 * 5;
            // Proportionale Länge beibehalten, aber auf 20-Tage-Jahr skalieren
            int yearDays = config.getSpringDays() + config.getSummerDays()
                + config.getFallDays() + config.getWinterDays();
            return (int) Math.round((double) base / yearDays * total);
        }
        return base;
    }

    public int getSeasonStartDay(Season season) {
        int startDay = 0;
        for (Season s : Season.values()) {
            if (s == season) return startDay;
            startDay += getDaysForSeason(s);
        }
        return startDay;
    }

    public Season getSeasonForDay(int dayOfYear) {
        int totalDays = getYearLengthDays();
        int d = ((dayOfYear % totalDays) + totalDays) % totalDays;
        int start = 0;
        for (Season season : Season.values()) {
            int days = getDaysForSeason(season);
            if (d < start + days) return season;
            start += days;
        }
        return Season.WINTER;
    }

    public int getDayInSeason(int dayOfYear) {
        int totalDays = getYearLengthDays();
        int d = ((dayOfYear % totalDays) + totalDays) % totalDays;
        int start = 0;
        for (Season season : Season.values()) {
            int days = getDaysForSeason(season);
            if (d < start + days) return d - start;
            start += days;
        }
        return d - start;
    }
}