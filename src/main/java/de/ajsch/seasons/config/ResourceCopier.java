package de.ajsch.seasons.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ResourceCopier {

    private final JavaPlugin plugin;

    public ResourceCopier(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void copyDefaults() {
        copyIfMissing("config.yml");
        copyIfMissing("precipitation_categories.yml");
        copyIfMissing("replaceable_plants.yml");
        copyIfMissing("foliage_tints.yml");
        copyIfMissing("season_colors.yml");
        copyIfMissing("frost.yml");
    }

    private void copyIfMissing(String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) {
            plugin.saveResource(resourceName, false);
            plugin.getLogger().info("Default " + resourceName + " copied to plugins/Seasons/");
        }
    }
}