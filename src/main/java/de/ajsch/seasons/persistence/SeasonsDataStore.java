package de.ajsch.seasons.persistence;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;

public class SeasonsDataStore {

    private final JavaPlugin plugin;
    private final String fileName;
    private final int saveIntervalMinutes;
    private File dataFile;

    private long yearStartOffset;

    public SeasonsDataStore(JavaPlugin plugin, String fileName, int saveIntervalMinutes) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.saveIntervalMinutes = saveIntervalMinutes;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), fileName);
        if (dataFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            yearStartOffset = yaml.getLong("year-start-offset", 0L);
        } else {
            yearStartOffset = 0L;
        }
        if (yearStartOffset == 0L) {
            yearStartOffset = plugin.getServer().getWorlds().get(0).getFullTime();
            save();
        }
        startAutoSave();
    }

    public long getYearStartOffset() {
        return yearStartOffset;
    }

    public void setYearStartOffset(long offset) {
        this.yearStartOffset = offset;
        save();
    }

    public void save() {
        if (dataFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("year-start-offset", yearStartOffset);
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + fileName + ": " + e.getMessage());
        }
    }

    private void startAutoSave() {
        long intervalTicks = saveIntervalMinutes * 60L * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                save();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }
}