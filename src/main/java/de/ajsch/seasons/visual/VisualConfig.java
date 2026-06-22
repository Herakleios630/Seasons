package de.ajsch.seasons.visual;

import de.ajsch.seasons.season.Season;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads and wraps {@code foliage_tints.yml} configuration.
 * Provides per-season default tints and per-biome overrides.
 */
public class VisualConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Cache: season -> default tint (0xRRGGBB int)
    private final Map<Season, Integer> defaultTints = new HashMap<>();

    // Cache: season -> (biome key string -> override tint)
    private final Map<Season, Map<String, Integer>> overrides = new HashMap<>();

    private double transitionDays = 4.0;
    private long updateIntervalTicks = 200L;

    public VisualConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "foliage_tints.yml");
        if (!configFile.exists()) {
            plugin.saveResource("foliage_tints.yml", false);
            plugin.getLogger().info("[VisualConfig] Created default foliage_tints.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        parse();
    }

    public void reload() {
        defaultTints.clear();
        overrides.clear();
        load();
    }

    private void parse() {
        transitionDays = config.getDouble("foliage.transition-days", 4.0);
        updateIntervalTicks = config.getLong("foliage.update-interval-ticks", 200L);

        for (Season season : Season.values()) {
            String path = "foliage.seasons." + season.name();
            String defaultHex = config.getString(path + ".default-tint", null);
            if (defaultHex != null) {
                try {
                    defaultTints.put(season, parseHexColor(defaultHex));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning(
                        "[VisualConfig] Invalid default-tint for " + season.name() + ": " + defaultHex);
                }
            }

            // Per-biome overrides
            Map<String, Integer> biomeMap = new HashMap<>();
            if (config.isConfigurationSection(path + ".overrides")) {
                for (String biomeName : config.getConfigurationSection(path + ".overrides").getKeys(false)) {
                    String hex = config.getString(path + ".overrides." + biomeName);
                    if (hex != null) {
                        try {
                            biomeMap.put(biomeName.toUpperCase(java.util.Locale.ROOT), parseHexColor(hex));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning(
                                "[VisualConfig] Invalid override color for " + season.name()
                                + "/" + biomeName + ": " + hex);
                        }
                    }
                }
            }
            overrides.put(season, biomeMap);
        }

        plugin.getLogger().info("[VisualConfig] Loaded " + defaultTints.size() + " season tints, "
            + overrides.values().stream().mapToInt(Map::size).sum() + " biome overrides");
    }

    // ------------------------------------------------------------
    //  Public getters
    // ------------------------------------------------------------

    /** Get the default foliage tint for a season, or fallback if not configured. */
    public int getDefaultTint(Season season) {
        Integer tint = defaultTints.get(season);
        if (tint != null) return tint;
        return getFallbackTint(season);
    }

    /** Get the per-biome override tint for a season, or -1 if none configured. */
    public int getOverrideTint(Season season, org.bukkit.block.Biome biome) {
        Map<String, Integer> biomeMap = overrides.get(season);
        if (biomeMap == null || biomeMap.isEmpty()) return -1;

        String key = biome.getKey().getKey().toUpperCase(java.util.Locale.ROOT);
        Integer tint = biomeMap.get(key);
        return tint != null ? tint : -1;
    }

    /** Days over which color transitions should be interpolated. */
    public double getTransitionDays() {
        return transitionDays;
    }

    /** Ticks between periodic foliage tint updates during transitions. */
    public long getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    /** Check whether any configuration was loaded. */
    public boolean hasConfig() {
        return !defaultTints.isEmpty();
    }

    // ------------------------------------------------------------
    //  Fallback colors (in case foliage_tints.yml is missing)
    // ------------------------------------------------------------

    private static int getFallbackTint(Season season) {
        return switch (season) {
            case SPRING -> 0x8FD15F;
            case SUMMER -> 0x7C9E4F;
            case FALL   -> 0xC68A3F;
            case WINTER -> 0xA8B0B8;
        };
    }

    // ------------------------------------------------------------
    //  Helper: parse "0xRRGGBB" or "#RRGGBB" to int
    // ------------------------------------------------------------

    static int parseHexColor(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Color string is null or empty");
        }
        String hex = input.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        } else if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            throw new IllegalArgumentException("Color must be 6 hex digits: " + input);
        }
        // Parse as RGB (not ARGB) – use 0xFF alpha (fully opaque)
        int rgb = Integer.parseInt(hex, 16);
        return rgb; // 0x00RRGGBB
    }
}