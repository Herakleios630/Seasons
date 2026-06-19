package de.ajsch.seasons.temperature;

import de.ajsch.seasons.config.ConfigManager;

public class TemperatureConfig {

    private final ConfigManager config;

    public TemperatureConfig(ConfigManager config) {
        this.config = config;
    }

    public double getMinWinter() { return config.getMinWinter(); }
    public double getMaxSummer() { return config.getMaxSummer(); }
    public double getDayNightAmplitude() { return config.getDayNightAmplitude(); }

    public double getBiomeOffset(boolean isCold, boolean isArid) {
        if (isCold) return config.getBiomeOffsetCold();
        if (isArid) return config.getBiomeOffsetArid();
        return config.getBiomeOffsetDefault();
    }
}