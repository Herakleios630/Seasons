package de.ajsch.seasons.visual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Crash-sichere Persistenz der Original-Biome pro Chunk.
 *
 * Speichert beim ersten Touch eines Chunks dessen ursprüngliche Biome
 * als JSON auf Platte, sodass nach einem Server-Crash die Original-Biome
 * wiederhergestellt werden können.
 */
public class BiomeBackupStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type BACKUPS_TYPE = new TypeToken<Map<String, String[]>>() {}.getType();

    private final Path dataFile;
    private final Logger logger;
    private UUID worldUid;
    private final Map<String, Biome[]> backups = new HashMap<>();

    public BiomeBackupStore(Path dataFolder, Logger logger) {
        this.logger = logger;
        this.dataFile = dataFolder.resolve("biome_backups.json");
    }

    /**
     * Speichert die Original-Biome eines Chunks, aber nur wenn
     * noch kein Backup für diesen Chunk existiert (first-touch).
     */
    public void saveFirstTouch(Chunk chunk, Biome[] originalBiomes) {
        String key = chunkKey(chunk);
        if (backups.containsKey(key)) return;

        backups.put(key, originalBiomes.clone());
        if (worldUid == null) {
            worldUid = chunk.getWorld().getUID();
        }
    }

    /**
     * Lädt alle Backups von der Platte und validiert die World-UID.
     * Bei korruptem JSON wird mit einer leeren Map gestartet.
     */
    public void loadAll() {
        if (!Files.exists(dataFile)) {
            logger.info("[BiomeBackupStore] Keine Backup-Datei gefunden – starte frisch.");
            return;
        }

        try {
            String json = Files.readString(dataFile);
            BackupFileData data = GSON.fromJson(json, BackupFileData.class);
            if (data == null || data.backups == null) {
                logger.warning("[BiomeBackupStore] Backup-Datei war leer oder unlesbar.");
                return;
            }

            if (data.world_uid != null && !data.world_uid.isEmpty()) {
                worldUid = UUID.fromString(data.world_uid);
            }

            int loaded = 0;
            int failed = 0;
            for (Map.Entry<String, String[]> entry : data.backups.entrySet()) {
                Biome[] biomeArray = new Biome[entry.getValue().length];
                boolean entryFailed = false;
                for (int i = 0; i < entry.getValue().length; i++) {
                    biomeArray[i] = resolveBiome(entry.getValue()[i]);
                    if (biomeArray[i] == null) {
                        entryFailed = true;
                    }
                }
                if (entryFailed) {
                    failed++;
                } else {
                    backups.put(entry.getKey(), biomeArray);
                    loaded++;
                }
            }

            logger.info(String.format(
                "[BiomeBackupStore] %d Backups geladen, %d fehlerhafte Einträge übersprungen.",
                loaded, failed));

        } catch (Exception e) {
            logger.warning("[BiomeBackupStore] JSON korrupt – starte mit leerem Backup: " + e.getMessage());
            backups.clear();
        }
    }

    /**
     * Serialisiert die aktuellen Backups atomar auf die Platte:
     * erst in temp-Datei schreiben, dann umbenennen.
     */
    public void saveAll(World world) {
        if (world != null) {
            worldUid = world.getUID();
        }

        BackupFileData data = new BackupFileData();
        data.world_uid = (worldUid != null) ? worldUid.toString() : null;
        data.backups = new HashMap<>();

        for (Map.Entry<String, Biome[]> entry : backups.entrySet()) {
            Biome[] biomes = entry.getValue();
            String[] names = new String[biomes.length];
            for (int i = 0; i < biomes.length; i++) {
                if (biomes[i] != null) {
                    NamespacedKey key = biomes[i].getKey();
                    if ("minecraft".equals(key.getNamespace())) {
                        names[i] = biomes[i].name();
                    } else {
                        names[i] = key.toString();
                    }
                }
            }
            data.backups.put(entry.getKey(), names);
        }

        try {
            String json = GSON.toJson(data);
            Path tempFile = dataFile.resolveSibling("biome_backups.json.tmp");
            Files.writeString(tempFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempFile, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("[BiomeBackupStore] Fehler beim Speichern der Backups: " + e.getMessage());
        }
    }

    public Biome[] getBackup(String chunkKey) {
        return backups.get(chunkKey);
    }

    public void removeBackup(String chunkKey) {
        backups.remove(chunkKey);
    }

    public boolean hasBackup(String chunkKey) {
        return backups.containsKey(chunkKey);
    }

    public void clear() {
        backups.clear();
    }

    public int size() {
        return backups.size();
    }

    /**
     * Gibt eine unveränderliche Sicht auf die aktuellen Backups (für Debug).
     */
    public Map<String, Biome[]> getBackups() {
        return Collections.unmodifiableMap(backups);
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Löst einen Biome-Namen oder NamespacedKey zu einem Biome auf.
     * Vanilla-Biome: via {@link Biome#valueOf(String)} (Enum, schnell).
     * Custom-Biome: via {@link Registry#BIOME} und {@link NamespacedKey}.
     *
     * @return das Biome oder {@code null} wenn nicht gefunden
     */
    private Biome resolveBiome(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // 1. Vanilla-Enum-Pfad (schnell, rückwärtskompatibel zu alten Backups)
        try {
            return Biome.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            // kein Vanilla-Biome → Registry-Lookup versuchen
        }

        // 2. Registry-Lookup für Custom-Biomes (z.B. "seasons:fall_forest")
        NamespacedKey key = NamespacedKey.fromString(name);
        if (key == null) {
            logger.warning("[BiomeBackupStore] Ungültiger Biome-Identifier: '" + name + "'");
            return null;
        }

        Biome biome = Registry.BIOME.get(key);
        if (biome == null) {
            logger.warning("[BiomeBackupStore] Biome nicht in Registry gefunden: '" + name + "'");
        }
        return biome;
    }

    // Interne Datenstruktur für JSON-Serialisierung
    private static class BackupFileData {
        String world_uid;
        Map<String, String[]> backups;
    }
}