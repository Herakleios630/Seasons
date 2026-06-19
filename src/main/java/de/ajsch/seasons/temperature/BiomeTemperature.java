package de.ajsch.seasons.temperature;

import de.ajsch.seasons.weather.PrecipitationCategory;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BiomeTemperature {

    private final File dataFolder;
    private final Map<String, PrecipitationCategory> categoryMap = new HashMap<>();

    public BiomeTemperature(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() {
        categoryMap.clear();
        File file = new File(dataFolder, "precipitation_categories.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (PrecipitationCategory cat : PrecipitationCategory.values()) {
            List<String> biomes = yaml.getStringList("categories." + cat.name());
            for (String biome : biomes) {
                categoryMap.put(biome.toUpperCase(), cat);
            }
        }
    }

    public void reload() {
        load();
    }

    public PrecipitationCategory getCategory(org.bukkit.block.Biome biome) {
        return categoryMap.getOrDefault(biome.name(), PrecipitationCategory.CAN_FREEZE);
    }

    public boolean isColdBiome(org.bukkit.block.Biome biome) {
        String name = biome.name();
        return name.contains("SNOWY") || name.contains("FROZEN") || name.contains("ICE")
            || name.contains("TAIGA") || name.contains("GROVE");
    }

    public boolean isAridBiome(org.bukkit.block.Biome biome) {
        String name = biome.name();
        return name.contains("DESERT") || name.contains("BADLANDS")
            || name.contains("SAVANNA") || name.contains("ARID");
    }
}