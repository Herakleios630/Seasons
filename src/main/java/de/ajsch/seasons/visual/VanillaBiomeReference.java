package de.ajsch.seasons.visual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads Vanilla biome JSONs from the plugin JAR resource folder {@code vanilla_biomes/}
 * and provides the original grass_color and foliage_color for each biome.
 * <p>
 * Minecraft 1.21.5+ stores color information in {@code effects.grass_color} and
 * {@code effects.foliage_color}. Biomes without explicit colors (the majority)
 * use the Vanilla colormap - we fall back to the standard Forest biome colors
 * ({@code 0x68B040} / {@code 0x3C9A1E}) for those.
 */
public class VanillaBiomeReference {

    private final Map<Biome, Integer> grassColors = new HashMap<>();
    private final Map<Biome, Integer> foliageColors = new HashMap<>();
    private final Map<Biome, String> rawJsonCache = new HashMap<>();

    private static final int DEFAULT_GRASS = 0x68B040;
    private static final int DEFAULT_FOLIAGE = 0x3C9A1E;

    /**
     * Loads all JSON files from the JAR resource folder {@code vanilla_biomes/}
     * and extracts grass/foliage color values by iterating over all registered biomes.
     *
     * @param plugin the plugin instance (used for resource access)
     * @return the number of successfully loaded biome color entries
     */
    public int loadFromResources(JavaPlugin plugin) {
        grassColors.clear();
        foliageColors.clear();
        rawJsonCache.clear();

        int loaded = 0;
        for (Biome biome : Registry.BIOME) {
            NamespacedKey key = biome.getKey();
            if (key == null) continue;
            String fileName = key.getKey() + ".json";
            if (tryLoadBiomeJson(plugin, fileName, biome)) {
                loaded++;
            }
        }

        plugin.getLogger().info(String.format(
                "[VanillaBiomeReference] %d biomes loaded with color data", loaded));
        return loaded;
    }

    private boolean tryLoadBiomeJson(JavaPlugin plugin, String fileName, Biome biome) {
        String resourcePath = "vanilla_biomes/" + fileName;
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) return false;

            String jsonStr;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                jsonStr = sb.toString();
            }

            // Cache the raw JSON for later use by BiomeJsonGenerator
            rawJsonCache.put(biome, jsonStr);

            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonObject effects = root.getAsJsonObject("effects");
            if (effects == null) return false;

            boolean hasData = false;

            JsonElement grassEl = effects.get("grass_color");
            if (grassEl != null && grassEl.isJsonPrimitive()) {
                int color = parseHexColor(grassEl.getAsString());
                if (color >= 0) {
                    grassColors.put(biome, color);
                    hasData = true;
                }
            }

            JsonElement foliageEl = effects.get("foliage_color");
            if (foliageEl != null && foliageEl.isJsonPrimitive()) {
                int color = parseHexColor(foliageEl.getAsString());
                if (color >= 0) {
                    foliageColors.put(biome, color);
                    hasData = true;
                }
            }

            // Even if no explicit colors exist, register the biome so hasColor() works
            if (!hasData) {
                grassColors.putIfAbsent(biome, -1);
                foliageColors.putIfAbsent(biome, -1);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[VanillaBiomeReference] Failed to parse " + resourcePath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the raw vanilla biome JSON string for the given biome,
     * or {@code null} if the JSON was not loaded.
     */
    public String getRawJson(Biome biome) {
        return rawJsonCache.get(biome);
    }

    private int parseHexColor(String hex) {
        if (hex == null) return -1;
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(clean, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns the Vanilla grass color for the given biome, or the default
     * Forest grass color ({@code 0x68B040}) if the biome has no explicit color.
     */
    public int getGrassColor(Biome biome) {
        Integer color = grassColors.get(biome);
        if (color == null || color < 0) return DEFAULT_GRASS;
        return color;
    }

    /**
     * Returns the Vanilla foliage color for the given biome, or the default
     * Forest foliage color ({@code 0x3C9A1E}) if the biome has no explicit color.
     */
    public int getFoliageColor(Biome biome) {
        Integer color = foliageColors.get(biome);
        if (color == null || color < 0) return DEFAULT_FOLIAGE;
        return color;
    }

    /**
     * Returns whether color data is available for this biome
     * (i.e. the JSON was loaded successfully).
     */
    public boolean hasColor(Biome biome) {
        return grassColors.containsKey(biome) || foliageColors.containsKey(biome);
    }

    /** Returns the number of biomes with loaded color data. */
    public int size() {
        return Math.max(grassColors.size(), foliageColors.size());
    }
}
