package de.ajsch.seasons.weather;

import de.ajsch.seasons.config.ConfigManager;

public class WeatherConfig {

    private final ConfigManager config;

    public WeatherConfig(ConfigManager config) {
        this.config = config;
    }

    public boolean isEnabled() { return config.isWeatherEnabled(); }
    public double getFreezeThreshold() { return config.getFreezeThreshold(); }
    public int getMaxNaturalHeight() { return config.getMaxNaturalHeight(); }
    public int getHeightPerCold() { return config.getHeightPerCold(); }
    public double getMeltThreshold() { return config.getMeltThreshold(); }
    public int getMeltSpeed() { return config.getMeltSpeed(); }
    public int getLayersPerScan() { return config.getLayersPerScan(); }
    public int getMinNeighborsForGrowth() { return config.getMinNeighborsForGrowth(); }
    public int getParticleRadius() { return config.getParticleRadius(); }
    public int getParticleCount() { return config.getParticleCount(); }
    public int getParticleYMin() { return config.getParticleYMin(); }
    public int getParticleYMax() { return config.getParticleYMax(); }
    public boolean getSnowMeltBonemeal() { return config.getSnowMeltBonemeal(); }
    public boolean getSpringRegenerationBonemeal() { return config.getSpringRegenerationBonemeal(); }
    public boolean isDebugMode() { return config.isDebugMode(); }
    public int getFirstSnowMinLayers() { return config.getFirstSnowMinLayers(); }
    public int getFirstSnowMaxLayers() { return config.getFirstSnowMaxLayers(); }
    public int getMaxAttemptsMultiplier() { return config.getMaxAttemptsMultiplier(); }
    public int getMeltChunksPerTick() { return config.getMeltChunksPerTick(); }
    public int getSummaryIntervalScans() { return config.getSummaryIntervalScans(); }
    public int getMaxDownSearchTicks() { return config.getMaxDownSearchTicks(); }
}