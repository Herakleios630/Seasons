package de.ajsch.seasons.weather;

import de.ajsch.seasons.config.ConfigManager;
import org.bukkit.Material;

import java.util.Set;

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
    public int getGrowthLayersPerScan() { return config.getGrowthLayersPerScan(); }
    public double getSaturationThreshold() { return config.getSaturationThreshold(); }
    public int getCacheTempLevelTolerance() { return config.getCacheTempLevelTolerance(); }
    public int getParticleRadius() { return config.getParticleRadius(); }
    public int getParticleCount() { return config.getParticleCount(); }
    public int getParticleYMin() { return config.getParticleYMin(); }
    public int getParticleYMax() { return config.getParticleYMax(); }
    public boolean getSnowMeltBonemeal() { return config.getSnowMeltBonemeal(); }
    public boolean getSpringRegenerationBonemeal() { return config.getSpringRegenerationBonemeal(); }
    public boolean isDebugMode() { return config.isDebugMode(); }
    public int getCacheTTLSeconds() { return config.getCacheTTLSeconds(); }
    public int getMeltChunksPerTick() { return config.getMeltChunksPerTick(); }
    public int getMeltLayersPerChunk() { return config.getMeltLayersPerChunk(); }
    public int getSummaryIntervalScans() { return config.getSummaryIntervalScans(); }

    public Set<Material> getReplaceablePlants() { return config.getReplaceablePlants(); }
    public Set<Material> getDoublePlants() { return config.getDoublePlants(); }
}