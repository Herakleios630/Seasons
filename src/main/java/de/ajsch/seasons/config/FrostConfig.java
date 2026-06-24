package de.ajsch.seasons.config;

import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads and wraps {@code frost.yml} configuration for the Frost system (Phase 2b).
 * Provides freeze thresholds, frost biome colors, particle settings, and excluded biomes.
 */
public class FrostConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    private boolean enabled;
    private double freezeThreshold;
    private double fullFrostThreshold;
    private double frostBiomeTemperature;
    private int targetGrassColor;
    private int targetFoliageColor;
    private boolean particlesEnabled;
    private String particleType;
    private int particlesPerSecond;
    private double spreadRadius;
    private final Set<Biome> excludedBiomes = new HashSet<>();

    public FrostConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "frost.yml");
        if (!file.exists()) {
            plugin.saveResource("frost.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        enabled = config.getBoolean("frost.enabled", true);
        freezeThreshold = config.getDouble("frost.freeze-threshold", 0.0);
        fullFrostThreshold = config.getDouble("frost.full-frost-threshold", -7.0);
        frostBiomeTemperature = config.getDouble("frost.biome-temperature", -0.5);

        targetGrassColor = parseHexColor(config.getString("frost.target-grass-color", "0xD0D4D8"));
        targetFoliageColor = parseHexColor(config.getString("frost.target-foliage-color", "0xC0C4C8"));

        particlesEnabled = config.getBoolean("frost.particles.enabled", true);
        particleType = config.getString("frost.particles.type", "SNOWFLAKE");
        particlesPerSecond = config.getInt("frost.particles.particles-per-second", 12);
        spreadRadius = config.getDouble("frost.particles.spread-radius", 3.0);

        excludedBiomes.clear();
        for (String name : config.getStringList("frost.excluded-biomes")) {
            try {
                excludedBiomes.add(Biome.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning(
                        "[FrostConfig] Unknown biome in excluded-biomes: " + name);
            }
        }

        plugin.getLogger().info(String.format(
                "[FrostConfig] Loaded: enabled=%s, freezeThreshold=%.1f, fullFrostThreshold=%.1f, "
                        + "biomeTemp=%.1f, grassColor=0x%06X, foliageColor=0x%06X, particles=%s, excludedBiomes=%d",
                enabled, freezeThreshold, fullFrostThreshold, frostBiomeTemperature,
                targetGrassColor, targetFoliageColor,
                particlesEnabled, excludedBiomes.size()));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getFreezeThreshold() {
        return freezeThreshold;
    }

    public double getFullFrostThreshold() {
        return fullFrostThreshold;
    }

    public double getFrostBiomeTemperature() {
        return frostBiomeTemperature;
    }

    public int getTargetGrassColor() {
        return targetGrassColor;
    }

    public int getTargetFoliageColor() {
        return targetFoliageColor;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public String getParticleType() {
        return particleType;
    }

    public int getParticlesPerSecond() {
        return particlesPerSecond;
    }

    public double getSpreadRadius() {
        return spreadRadius;
    }

    public Set<Biome> getExcludedBiomes() {
        return Collections.unmodifiableSet(excludedBiomes);
    }

    public boolean isFrostAllowedInBiome(Biome biome) {
        return !excludedBiomes.contains(biome);
    }

    private int parseHexColor(String hex) {
        if (hex == null) return 0xD0D4D8;
        String clean = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2)
                : hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (hex.startsWith("0x") || hex.startsWith("0X") || hex.startsWith("#")) {
                return Integer.parseInt(clean, 16);
            }
            try {
                return Integer.parseInt(clean, 10);
            } catch (NumberFormatException e) {
                return Integer.parseInt(clean, 16);
            }
        } catch (NumberFormatException e) {
            return 0xD0D4D8;
        }
    }
}