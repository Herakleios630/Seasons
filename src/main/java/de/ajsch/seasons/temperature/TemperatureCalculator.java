package de.ajsch.seasons.temperature;

import de.ajsch.seasons.season.SeasonConfig;
import org.bukkit.block.Biome;

public class TemperatureCalculator {

    private final SeasonConfig seasonConfig;
    private final TemperatureConfig tempConfig;
    private final BiomeTemperature biomeTemp;
    private Double overrideTemp = null;

    public TemperatureCalculator(SeasonConfig seasonConfig, TemperatureConfig tempConfig,
                                 BiomeTemperature biomeTemp) {
        this.seasonConfig = seasonConfig;
        this.tempConfig = tempConfig;
        this.biomeTemp = biomeTemp;
    }

    public void setOverride(Double temp) { this.overrideTemp = temp; }
    public void resetOverride() { this.overrideTemp = null; }
    public boolean hasOverride() { return overrideTemp != null; }

    public double calculate(int dayOfYear, Biome biome) {
        if (overrideTemp != null) return overrideTemp;

        int yearLength = seasonConfig.getYearLengthDays();
        double minWinter = tempConfig.getMinWinter();
        double maxSummer = tempConfig.getMaxSummer();
        double amplitude = (maxSummer - minWinter) / 2.0;
        double offset = (maxSummer + minWinter) / 2.0;
        double phaseShift = yearLength * 0.25;

        double base = amplitude * Math.cos(2.0 * Math.PI * (dayOfYear - phaseShift) / yearLength) + offset;

        boolean isCold = biomeTemp.isColdBiome(biome);
        boolean isArid = biomeTemp.isAridBiome(biome);
        double biomeOffset = tempConfig.getBiomeOffset(isCold, isArid);

        return base + biomeOffset;
    }

    public double calculateWithDayTime(int dayOfYear, Biome biome, long worldTime) {
        double base = calculate(dayOfYear, biome);
        double dayProgress = (worldTime % 24000L) / 24000.0;
        double dayMod = tempConfig.getDayNightAmplitude() * Math.sin(dayProgress * 2.0 * Math.PI);
        return base + dayMod;
    }
}