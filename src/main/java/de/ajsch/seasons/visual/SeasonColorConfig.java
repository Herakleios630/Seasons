package de.ajsch.seasons.visual;

import de.ajsch.seasons.season.Season;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and wraps {@code season_colors.yml}.
 * Provides grass/foliage color targets per original biome and season,
 * transition steps per season pair, and the list of enabled biomes.
 */
public class SeasonColorConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // --- Parsed defaults ---
    private final EnumMap<Season, Double> defaultBlendFactors = new EnumMap<>(Season.class);
    private final EnumMap<Season, Integer> defaultGrassTargets = new EnumMap<>(Season.class);
    private final EnumMap<Season, Integer> defaultFoliageTargets = new EnumMap<>(Season.class);
    private final EnumMap<Season, Integer> defaultFogColors = new EnumMap<>(Season.class);
    private final EnumMap<Season, Integer> defaultSkyColors = new EnumMap<>(Season.class);
    private final EnumMap<Season, Integer> defaultWaterFogColors = new EnumMap<>(Season.class);

    // --- Biome overrides: Map<biomeKey, Map<Season, target>> ---
    private final Map<String, EnumMap<Season, Integer>> biomeGrassOverrides = new HashMap<>();
    private final Map<String, EnumMap<Season, Integer>> biomeFoliageOverrides = new HashMap<>();

    // --- Transition steps per pair "FROM_TO" ---
    private final Map<String, Integer> transitionSteps = new HashMap<>();
    private int defaultSteps = 4;

    // --- Enabled biomes ---
    private List<String> enabledBiomeKeys = new ArrayList<>();

    // --- Cached Biome enum -> key string mapping ---
    private final Map<Biome, String> biomeKeyCache = new HashMap<>();

    public SeasonColorConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads the configuration from {@code plugins/Seasons/season_colors.yml}.
     */
    public void reloadFromConfig() {
        File file = new File(plugin.getDataFolder(), "season_colors.yml");
        if (!file.exists()) {
            plugin.saveResource("season_colors.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        parseDefaults();
        parseBiomeOverrides();
        parseTransitions();
        parseEnabledBiomes();
        biomeKeyCache.clear();
        plugin.getLogger().info(String.format(
                "[SeasonColorConfig] Loaded: %d seasons, %d biome overrides, %d enabled biomes",
                defaultGrassTargets.size(), biomeGrassOverrides.size(), enabledBiomeKeys.size()));
    }

    // ------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------

    private void parseDefaults() {
        defaultBlendFactors.clear();
        defaultGrassTargets.clear();
        defaultFoliageTargets.clear();

        ConfigurationSection defaultsSec = config.getConfigurationSection("defaults");
        if (defaultsSec == null) return;

        for (String seasonName : defaultsSec.getKeys(false)) {
            Season season = parseSeason(seasonName);
            if (season == null) {
                plugin.getLogger().warning(
                        "[SeasonColorConfig] Unknown season in defaults: " + seasonName);
                continue;
            }
            ConfigurationSection sec = defaultsSec.getConfigurationSection(seasonName);
            if (sec == null) continue;

            defaultBlendFactors.put(season, sec.getDouble("blend_factor", 0.0));
            String grassStr = sec.getString("grass_color_target");
            if (grassStr != null) {
                int color = parseHexColor(grassStr);
                if (color >= 0) defaultGrassTargets.put(season, color);
            }
            String foliageStr = sec.getString("foliage_color_target");
            if (foliageStr != null) {
                int color = parseHexColor(foliageStr);
                if (color >= 0) defaultFoliageTargets.put(season, color);
            }

            String fogStr = sec.getString("fog_color");
            if (fogStr != null) {
                int color = parseHexColor(fogStr);
                if (color >= 0) defaultFogColors.put(season, color);
            }
            String skyStr = sec.getString("sky_color");
            if (skyStr != null) {
                int color = parseHexColor(skyStr);
                if (color >= 0) defaultSkyColors.put(season, color);
            }
            String waterFogStr = sec.getString("water_fog_color");
            if (waterFogStr != null) {
                int color = parseHexColor(waterFogStr);
                if (color >= 0) defaultWaterFogColors.put(season, color);
            }
        }
    }

    private void parseBiomeOverrides() {
        biomeGrassOverrides.clear();
        biomeFoliageOverrides.clear();

        ConfigurationSection biomesSec = config.getConfigurationSection("biomes");
        if (biomesSec == null) return;

        for (String biomeKey : biomesSec.getKeys(false)) {
            ConfigurationSection biomeSec = biomesSec.getConfigurationSection(biomeKey);
            if (biomeSec == null) continue;

            EnumMap<Season, Integer> grassMap = new EnumMap<>(Season.class);
            EnumMap<Season, Integer> foliageMap = new EnumMap<>(Season.class);

            for (String seasonName : biomeSec.getKeys(false)) {
                Season season = parseSeason(seasonName);
                if (season == null) {
                    plugin.getLogger().warning(
                            "[SeasonColorConfig] Unknown season in biomes." + biomeKey + ": " + seasonName);
                    continue;
                }
                ConfigurationSection sec = biomeSec.getConfigurationSection(seasonName);
                if (sec == null) continue;

                String grassStr = sec.getString("grass_color_target");
                if (grassStr != null) {
                    int color = parseHexColor(grassStr);
                    if (color >= 0) grassMap.put(season, color);
                }
                String foliageStr = sec.getString("foliage_color_target");
                if (foliageStr != null) {
                    int color = parseHexColor(foliageStr);
                    if (color >= 0) foliageMap.put(season, color);
                }
            }

            if (!grassMap.isEmpty()) biomeGrassOverrides.put(biomeKey, grassMap);
            if (!foliageMap.isEmpty()) biomeFoliageOverrides.put(biomeKey, foliageMap);
        }
    }

    private void parseTransitions() {
        transitionSteps.clear();
        defaultSteps = config.getInt("transitions.default_steps", 4);

        ConfigurationSection transSec = config.getConfigurationSection("transitions");
        if (transSec == null) return;

        for (String key : transSec.getKeys(false)) {
            if (key.equals("default_steps")) continue;
            transitionSteps.put(key, transSec.getInt(key, defaultSteps));
        }
    }

    private void parseEnabledBiomes() {
        enabledBiomeKeys = config.getStringList("enabled_biomes");
    }

    // ------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------

    /**
     * Returns the grass color target for a biome in a season.
     * Respects per-biome overrides; falls back to the default per-season target.
     */
    public int getGrassColorTarget(Biome original, Season season) {
        String key = biomeKey(original);
        EnumMap<Season, Integer> overrides = biomeGrassOverrides.get(key);
        if (overrides != null && overrides.containsKey(season)) {
            return overrides.get(season);
        }
        Integer def = defaultGrassTargets.get(season);
        return def != null ? def : 0x68B040;
    }

    /**
     * Returns the foliage color target for a biome in a season.
     */
    public int getFoliageColorTarget(Biome original, Season season) {
        String key = biomeKey(original);
        EnumMap<Season, Integer> overrides = biomeFoliageOverrides.get(key);
        if (overrides != null && overrides.containsKey(season)) {
            return overrides.get(season);
        }
        Integer def = defaultFoliageTargets.get(season);
        return def != null ? def : 0x3C9A1E;
    }

    /**
     * Returns the fog color for a season, or null if not configured.
     */
    public Integer getFogColor(Season season) {
        return defaultFogColors.get(season);
    }

    /**
     * Returns the sky color for a season, or null if not configured.
     */
    public Integer getSkyColor(Season season) {
        return defaultSkyColors.get(season);
    }

    /**
     * Returns the water fog color for a season, or null if not configured.
     */
    public Integer getWaterFogColor(Season season) {
        return defaultWaterFogColors.get(season);
    }

    /**
     * Returns the blend factor for a season (0.0 = original, 1.0 = full target).
     */
    public double getBlendFactor(Season season) {
        Double factor = defaultBlendFactors.get(season);
        return factor != null ? factor : 1.0;
    }

    /**
     * Returns the number of transition steps (sub-variants) for a given
     * from-to season pair, e.g. {@code SUMMER} to {@code FALL}.
     */
    public int getTransitionSteps(Season from, Season to) {
        String pairKey = from.name() + "_TO_" + to.name();
        Integer steps = transitionSteps.get(pairKey);
        return steps != null ? steps : defaultSteps;
    }

    /**
     * Returns the list of enableed biome keys (e.g. "minecraft:forest").
     */
    public List<String> getEnabledBiomes() {
        return Collections.unmodifiableList(enabledBiomeKeys);
    }

    /**
     * Checks whether a given biome has an override entry in the config
     * (grass or foliage).
     */
    public boolean hasBiomeOverride(Biome biome, Season season) {
        String key = biomeKey(biome);
        EnumMap<Season, Integer> grassOver = biomeGrassOverrides.get(key);
        EnumMap<Season, Integer> foliageOver = biomeFoliageOverrides.get(key);
        boolean grassHas = grassOver != null && grassOver.containsKey(season);
        boolean foliageHas = foliageOver != null && foliageOver.containsKey(season);
        return grassHas || foliageHas;
    }

    /**
     * Returns true if the biome is in the enabled_biomes list.
     */
    public boolean isBiomeEnabled(Biome biome) {
        return enabledBiomeKeys.contains(biomeKey(biome));
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private String biomeKey(Biome biome) {
        return biomeKeyCache.computeIfAbsent(biome, b -> {
            NamespacedKey key = b.getKey();
            if (key != null) return key.getNamespace() + ":" + key.getKey();
            return b.getKey().getKey().toLowerCase();
        });
    }

    private Season parseSeason(String name) {
        try {
            return Season.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int parseHexColor(String hex) {
        if (hex == null) return -1;
        // Remove prefix if present
        String clean = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2)
                : hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            // If the original had a prefix, always parse as hex
            if (hex.startsWith("0x") || hex.startsWith("0X") || hex.startsWith("#")) {
                return Integer.parseInt(clean, 16);
            }
            // No prefix: could be decimal (SnakeYAML parsed 0xHHHHHH as int) or
            // pure hex string like "FF0000". Try decimal first; fall back to hex.
            try {
                return Integer.parseInt(clean, 10);
            } catch (NumberFormatException e) {
                return Integer.parseInt(clean, 16);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
