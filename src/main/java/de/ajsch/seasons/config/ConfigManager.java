package de.ajsch.seasons.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        load();
    }

    // Season
    public int getYearLengthDays() {
        return config.getInt("season.year-length-days", 365);
    }

    public int getSpringDays() {
        return config.getInt("season.spring-days", 91);
    }

    public int getSummerDays() {
        return config.getInt("season.summer-days", 91);
    }

    public int getFallDays() {
        return config.getInt("season.fall-days", 91);
    }

    public int getWinterDays() {
        return config.getInt("season.winter-days", 92);
    }

    public boolean isDebugMode() {
        return config.getBoolean("season.debug-mode", false);
    }

    // Temperature
    public double getMinWinter() {
        return config.getDouble("temperature.min-winter", -0.5);
    }

    public double getMaxSummer() {
        return config.getDouble("temperature.max-summer", 0.8);
    }

    public double getBiomeOffsetDefault() {
        return config.getDouble("temperature.biome-offsets.default", 0.0);
    }

    public double getBiomeOffsetCold() {
        return config.getDouble("temperature.biome-offsets.cold", -0.3);
    }

    public double getBiomeOffsetArid() {
        return config.getDouble("temperature.biome-offsets.arid", 0.2);
    }

    public double getBiomeOffsetTemperate() {
        return config.getDouble("temperature.biome-offsets.temperate", 0.0);
    }

    public double getDayNightAmplitude() {
        return config.getDouble("temperature.day-night-amplitude", 0.15);
    }

    // Weather
    public boolean isWeatherEnabled() {
        return config.getBoolean("weather.enabled", true);
    }

    public double getFreezeThreshold() {
        return config.getDouble("weather.snow.freeze-threshold", 0.0);
    }

    public int getMaxNaturalHeight() {
        return config.getInt("weather.snow.max-natural-height", 2);
    }

    public int getHeightPerCold() {
        return config.getInt("weather.snow.height-per-cold", 1);
    }

    public double getMeltThreshold() {
        return config.getDouble("weather.snow.melt-threshold", 0.5);
    }

    public int getMeltSpeed() {
        return config.getInt("weather.snow.melt-speed", 1);
    }

    public int getLayersPerScan() {
        return config.getInt("weather.snow.layers-per-scan", 3);
    }

    public int getMinNeighborsForGrowth() {
        return config.getInt("weather.snow.min-neighbors-for-growth", 3);
    }

    public int getParticleRadius() {
        return config.getInt("weather.snow.particle-radius", 32);
    }

    public int getParticleCount() {
        return config.getInt("weather.snow.particle-count", 50);
    }

    public int getParticleYMin() {
        return config.getInt("weather.snow.particle-y-min", 15);
    }

    public int getParticleYMax() {
        return config.getInt("weather.snow.particle-y-max", 30);
    }

    public boolean getSnowMeltBonemeal() {
        return config.getBoolean("weather.snow.snow-melt-bonemeal", true);
    }

    public boolean getSpringRegenerationBonemeal() {
        return config.getBoolean("weather.snow.spring-regeneration-bonemeal", true);
    }

    public int getFirstSnowMinLayers() {
        return config.getInt("weather.snow.first-snow-min-layers", 3);
    }

    public int getFirstSnowMaxLayers() {
        return config.getInt("weather.snow.first-snow-max-layers", 5);
    }

    public int getMaxAttemptsMultiplier() {
        return config.getInt("weather.snow.max-attempts-multiplier", 16);
    }

    public int getMaxDownSearchTicks() {
        return config.getInt("weather.snow.max-down-search", 32);
    }

    // Persistence
    public String getPersistenceFile() {
        return config.getString("persistence.file", "seasons_data.yml");
    }

    public int getSaveIntervalMinutes() {
        return config.getInt("persistence.save-interval-minutes", 5);
    }

    // Commands
    public boolean isCommandsEnabled() {
        return config.getBoolean("commands.enabled", true);
    }

    public boolean isDebugRequiresOp() {
        return config.getBoolean("commands.debug-requires-op", true);
    }

    // Performance
    public int getChunkScanIntervalTicks() {
        return config.getInt("performance.chunk-scan-interval-ticks", 600);
    }

    public int getMaxSnowChunksPerTick() {
        return config.getInt("performance.max-snow-chunks-per-tick", 4);
    }

    public int getMeltChunksPerTick() {
        return config.getInt("performance.melt-chunks-per-tick", 8);
    }

    public int getSummaryIntervalScans() {
        return config.getInt("performance.summary-interval-scans", 50);
    }
}