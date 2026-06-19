package de.ajsch.seasons.season;

import de.ajsch.seasons.persistence.SeasonsDataStore;
import org.bukkit.World;

public class SeasonClock {

    private final World world;
    private final SeasonsDataStore dataStore;
    private final SeasonConfig seasonConfig;
    private double speedMultiplier = 1.0;

    public SeasonClock(World world, SeasonsDataStore dataStore, SeasonConfig seasonConfig) {
        this.world = world;
        this.dataStore = dataStore;
        this.seasonConfig = seasonConfig;
    }

    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = multiplier;
    }

    public World getWorld() { return world; }

    public SeasonsDataStore getDataStore() { return dataStore; }

    public SeasonConfig getSeasonConfig() { return seasonConfig; }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public int calculateDayOfYear() {
        long fullTime = world.getFullTime();
        long offset = dataStore.getYearStartOffset();
        long effectiveTicks = (long) ((fullTime - offset) * speedMultiplier);
        int daysPerYear = seasonConfig.getYearLengthDays();
        long ticksPerDay = 24000L;
        long totalDays = effectiveTicks / ticksPerDay;
        int day = (int) (((totalDays % daysPerYear) + daysPerYear) % daysPerYear);
        return day;
    }

    public Season getCurrentSeason() {
        return seasonConfig.getSeasonForDay(calculateDayOfYear());
    }

    public int getDayInSeason() {
        return seasonConfig.getDayInSeason(calculateDayOfYear());
    }

    public int getDaysRemainingInSeason() {
        Season current = getCurrentSeason();
        int totalDays = seasonConfig.getDaysForSeason(current);
        return totalDays - getDayInSeason();
    }

    public int getYear() {
        long fullTime = world.getFullTime();
        long offset = dataStore.getYearStartOffset();
        long effectiveTicks = (long) ((fullTime - offset) * speedMultiplier);
        long ticksPerDay = 24000L;
        long totalDays = effectiveTicks / ticksPerDay;
        int daysPerYear = seasonConfig.getYearLengthDays();
        return (int) (totalDays / daysPerYear) + 1;
    }

    public void skipDays(int days) {
        long newOffset = dataStore.getYearStartOffset() - (days * 24000L);
        dataStore.setYearStartOffset(newOffset);
    }

    public void setSeason(Season targetSeason) {
        int currentDay = calculateDayOfYear();
        int targetStartDay = seasonConfig.getSeasonStartDay(targetSeason);
        int daysToSkip = targetStartDay - currentDay;
        if (daysToSkip <= 0) daysToSkip += seasonConfig.getYearLengthDays();
        skipDays(daysToSkip);
    }
}