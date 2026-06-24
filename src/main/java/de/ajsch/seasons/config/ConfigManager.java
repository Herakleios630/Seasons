package de.ajsch.seasons.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private de.ajsch.seasons.visual.SeasonColorConfig seasonColors;
    private FrostConfig frostConfig;

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

    /** Reloads all configuration files including season_colors.yml. */
    public void reloadAll() {
        load();
        if (seasonColors != null) {
            seasonColors.reloadFromConfig();
        }
    }

    /** Returns the SeasonColorConfig instance, initializing it if needed. */
    public de.ajsch.seasons.visual.SeasonColorConfig getSeasonColors() {
        if (seasonColors == null) {
            seasonColors = new de.ajsch.seasons.visual.SeasonColorConfig(plugin);
            seasonColors.reloadFromConfig();
        }
        return seasonColors;
    }

    /** Returns the FrostConfig instance, initializing it if needed. */
    public FrostConfig getFrostConfig() {
        if (frostConfig == null) {
            frostConfig = new FrostConfig(plugin);
            frostConfig.load();
        }
        return frostConfig;
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

    // Debug-Overrides: Multiplier applied to snow rates when debug mode is active
    private double getDebugOverride(String key, double defaultValue) {
        return config.getDouble("season.debug-overrides." + key, defaultValue);
    }

    public double getSnowPlacementMultiplier() {
        return getDebugOverride("snow-placement-multiplier", 1.0);
    }

    public double getSnowGrowthMultiplier() {
        return getDebugOverride("snow-growth-multiplier", 1.0);
    }

    public double getSnowMeltMultiplier() {
        return getDebugOverride("snow-melt-multiplier", 1.0);
    }

    public double getMaxSnowChunksMultiplier() {
        return getDebugOverride("max-snow-chunks-multiplier", 1.0);
    }

    public double getMeltChunksMultiplier() {
        return getDebugOverride("melt-chunks-multiplier", 1.0);
    }

    private int applyDebugMultiplier(int rawValue, double multiplier) {
        if (isDebugMode()) {
            return Math.max(1, (int) Math.round(rawValue * multiplier));
        }
        return rawValue;
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
        int raw = config.getInt("weather.snow.layers-per-scan", 3);
        return applyDebugMultiplier(raw, getSnowPlacementMultiplier());
    }

    public int getGrowthLayersPerScan() {
        int raw = config.getInt("weather.snow.growth.layers-per-scan", 2);
        return applyDebugMultiplier(raw, getSnowGrowthMultiplier());
    }

    public double getSaturationThreshold() {
        return config.getDouble("weather.snow.growth.saturation-threshold", 0.95);
    }

    public int getCacheTempLevelTolerance() {
        return config.getInt("weather.snow.growth.cache.temp-level-tolerance", 0);
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
        int raw = config.getInt("performance.max-snow-chunks-per-tick", 4);
        return applyDebugMultiplier(raw, getMaxSnowChunksMultiplier());
    }

    public int getMeltChunksPerTick() {
        int raw = config.getInt("performance.melt-chunks-per-tick", 8);
        return applyDebugMultiplier(raw, getMeltChunksMultiplier());
    }

    public int getMeltLayersPerChunk() {
        int raw = config.getInt("weather.snow.melting.layers-per-chunk", 4);
        return applyDebugMultiplier(raw, getSnowMeltMultiplier());
    }

    public int getSummaryIntervalScans() {
        return config.getInt("performance.summary-interval-scans", 50);
    }

    public int getCacheTTLSeconds() {
        return config.getInt("weather.snow.growth.cache.ttl-seconds", 30);
    }

    // Cache Persistence
    public String getCacheFile() {
        return config.getString("cache.persistence.file", "chunk_cache.json");
    }

    public int getCacheSaveIntervalSeconds() {
        return config.getInt("weather.snow.growth.cache.save-interval-seconds", 5);
    }

    public int getCacheVersion() {
        return config.getInt("cache.persistence.version", 1);
    }

    // Biome spoofing from biome_spoof.yml
    private FileConfiguration biomeSpoofConfig;

    private FileConfiguration getBiomeSpoofConfig() {
        if (biomeSpoofConfig != null) return biomeSpoofConfig;
        File file = new File(plugin.getDataFolder(), "biome_spoof.yml");
        if (!file.exists()) {
            plugin.saveResource("biome_spoof.yml", false);
        }
        biomeSpoofConfig = YamlConfiguration.loadConfiguration(file);
        return biomeSpoofConfig;
    }

    public boolean isBiomeSpoofEnabled() {
        return getBiomeSpoofConfig().getBoolean("enabled", true);
    }

    public String getSpoofMode() {
        return getBiomeSpoofConfig().getString("mode", "GLOBAL_RING");
    }

    public int getSpoofRadiusChunks() {
        return getBiomeSpoofConfig().getInt("radius_chunks", 8);
    }

    public int getSpoofBudgetPerTick() {
        return getBiomeSpoofConfig().getInt("budget_chunks_per_tick", 16);
    }

    public int getSpoofTransitionDays() {
        return getBiomeSpoofConfig().getInt("transition_days", 3);
    }

    public boolean isRevertOnNonWinter() {
        return getBiomeSpoofConfig().getBoolean("revert_on_non_winter", true);
    }

    public int getTransitionNightsPerStep() {
        return getBiomeSpoofConfig().getInt("transition.nights_per_step", 1);
    }

    public org.bukkit.block.Biome getSeasonTargetBiome(
            de.ajsch.seasons.season.Season season) {
        String name = getBiomeSpoofConfig().getString("seasons." + season.name());
        if (name != null) {
            name = name.trim();
            // 1. Vanilla-Biome über Enum-Lookup
            try {
                return org.bukkit.block.Biome.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
            // 2. Custom-Biome über NamespacedKey / Registry
            try {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(name.toLowerCase());
                if (key != null) {
                    org.bukkit.block.Biome biome = org.bukkit.Registry.BIOME.get(key);
                    if (biome != null) return biome;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    public boolean isOceanSpoofEnabled() {
        return getBiomeSpoofConfig().getBoolean("oceans.enabled", true);
    }

    public boolean isKeepDeepOceanVariants() {
        return getBiomeSpoofConfig().getBoolean("oceans.keep_deep_variants", true);
    }

    public org.bukkit.block.Biome getOceanTargetBiome(
            de.ajsch.seasons.season.Season season) {
        String name = getBiomeSpoofConfig().getString("oceans.seasons." + season.name());
        if (name != null) {
            name = name.trim();
            // 1. Vanilla-Biome über Enum-Lookup
            try {
                return org.bukkit.block.Biome.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
            // 2. Custom-Biome über NamespacedKey / Registry
            try {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(name.toLowerCase());
                if (key != null) {
                    org.bukkit.block.Biome biome = org.bukkit.Registry.BIOME.get(key);
                    if (biome != null) return biome;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    public java.util.Set<org.bukkit.block.Biome> getExcludedBiomes() {
        java.util.List<String> names = getBiomeSpoofConfig().getStringList("excluded_biomes");
        java.util.Set<org.bukkit.block.Biome> set = new java.util.HashSet<>();
        for (String name : names) {
            try {
                set.add(org.bukkit.block.Biome.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    public java.util.List<String> getDisabledFxWorlds() {
        return getBiomeSpoofConfig().getStringList("disabled_fx_worlds");
    }

    public boolean isDiskBackupEnabled() {
        return getBiomeSpoofConfig().getBoolean("disk_backup.enabled", true);
    }

    public boolean isResendEnabled() {
        return getBiomeSpoofConfig().getBoolean("resend_enabled", true);
    }

    public int getResendChunksPerTick() {
        return getBiomeSpoofConfig().getInt("resend_chunks_per_tick", 8);
    }

    // Replaceable plants from replaceable_plants.yml
    private FileConfiguration plantsConfig;

    private FileConfiguration getPlantsConfig() {
        if (plantsConfig != null) return plantsConfig;
        File plantsFile = new File(plugin.getDataFolder(), "replaceable_plants.yml");
        if (!plantsFile.exists()) {
            plugin.saveResource("replaceable_plants.yml", false);
        }
        if (plantsFile.exists()) {
            plantsConfig = YamlConfiguration.loadConfiguration(plantsFile);
            // Diagnose: which material names are recognized?
            List<String> replaceable = plantsConfig.getStringList("replaceable");
            List<String> doublePlants = plantsConfig.getStringList("double_plants");
            int matched = 0, unmatched = 0;
            java.util.ArrayList<String> unmatchedNames = new java.util.ArrayList<>();
            for (String name : replaceable) {
                if (org.bukkit.Material.matchMaterial(name) != null) matched++;
                else { unmatched++; unmatchedNames.add(name); }
            }
            for (String name : doublePlants) {
                if (org.bukkit.Material.matchMaterial(name) != null) matched++;
                else { unmatched++; unmatchedNames.add(name); }
            }
            plugin.getLogger().info(String.format(
                "[ConfigManager] replaceable_plants.yml: %d materials matched, %d unmatched",
                matched, unmatched));
            if (unmatched > 0) {
                plugin.getLogger().warning(
                    "[ConfigManager] Unmatched material names: " + String.join(", ", unmatchedNames));
            }
        } else {
            plugin.getLogger().warning(
                "[ConfigManager] replaceable_plants.yml not found – no plants will be displaced!");
        }
        return plantsConfig;
    }

    public Set<Material> getReplaceablePlants() {
        FileConfiguration cfg = getPlantsConfig();
        if (cfg == null) return Collections.emptySet();
        List<String> names = cfg.getStringList("replaceable");
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) set.add(mat);
        }
        return set;
    }

    public Set<Material> getDoublePlants() {
        FileConfiguration cfg = getPlantsConfig();
        if (cfg == null) return Collections.emptySet();
        List<String> names = cfg.getStringList("double_plants");
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) set.add(mat);
        }
        return set;
    }
}