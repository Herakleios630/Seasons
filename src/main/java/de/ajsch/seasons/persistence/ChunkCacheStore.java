package de.ajsch.seasons.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.weather.ChunkCacheEntry;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous JSON persistence for the chunk snow cache.
 *
 * <p>Stores each {@link ChunkCacheEntry} as a JSON-serialized record keyed by
 * {@code worldUID:chunkX:chunkZ}. Byte arrays (pluginSnowHeight, naturalSnowHeight)
 * are Base64-encoded in the JSON output.</p>
 *
 * <p><b>Load behaviour:</b> On {@link #load()}, the JSON file is read, the
 * {@code cacheVersion} field is checked against the config value, and only
 * matching data is loaded into the in-memory cache. Mismatched versions
 * result in a fresh start (cache file is ignored).</p>
 *
 * <p><b>Save behaviour:</b> A dirty-flag system marks entries that need
 * writing. Every {@code saveIntervalSeconds} an async task collects all
 * dirty entries and flushes them to disk. On {@code onDisable}, a synchronous
 * {@link #save()} writes all remaining dirty entries.</p>
 */
public class ChunkCacheStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CHUNKS_TYPE = new TypeToken<Map<String, ChunkJsonEntry>>() {}.getType();

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ConcurrentHashMap<String, ChunkCacheEntry> cache;
    private final File cacheFile;
    private final ConcurrentHashMap<String, Boolean> dirtyMap = new ConcurrentHashMap<>();

    /**
     * Creates the store.
     *
     * @param plugin the owning plugin (used for data folder and scheduler)
     * @param config the config manager providing file path and intervals
     * @param cache  the in-memory chunk cache to persist/restore
     */
    public ChunkCacheStore(JavaPlugin plugin, ConfigManager config,
                           ConcurrentHashMap<String, ChunkCacheEntry> cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
        this.cacheFile = new File(plugin.getDataFolder(), config.getCacheFile());
    }

    /**
     * Loads the chunk cache from {@code chunk_cache.json}.
     *
     * <p>If the file does not exist or the stored {@code cacheVersion} does
     * not match {@link ConfigManager#getCacheVersion()}, the cache remains
     * empty and the old file is ignored (it will be overwritten on the next
     * save).</p>
     */
    public void load() {
        if (!cacheFile.exists()) {
            plugin.getLogger().info("No chunk cache file found, starting fresh.");
            return;
        }

        try (FileReader reader = new FileReader(cacheFile)) {
            CacheFileRoot root = GSON.fromJson(reader, CacheFileRoot.class);
            if (root == null || root.chunks == null) {
                plugin.getLogger().warning("Chunk cache file is empty or corrupt, starting fresh.");
                return;
            }

            int expectedVersion = config.getCacheVersion();
            if (root.cacheVersion != expectedVersion) {
                plugin.getLogger().info(String.format(
                    "Chunk cache version mismatch (stored=%d, expected=%d), starting fresh.",
                    root.cacheVersion, expectedVersion));
                return;
            }

            int loadedCount = 0;
            for (Map.Entry<String, ChunkJsonEntry> entry : root.chunks.entrySet()) {
                String key = entry.getKey();
                ChunkJsonEntry jsonEntry = entry.getValue();
                ChunkCacheEntry cacheEntry = fromJsonEntry(jsonEntry);
                if (cacheEntry != null) {
                    cache.put(key, cacheEntry);
                    loadedCount++;
                }
            }

            plugin.getLogger().info(String.format(
                "Loaded %d chunk cache entries from %s (version=%d).",
                loadedCount, cacheFile.getName(), root.cacheVersion));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load chunk cache: " + e.getMessage());
        }
    }

    /**
     * Synchronously saves all dirty cache entries to the JSON file.
     * Called during {@code onDisable}.
     */
    public void save() {
        File dir = cacheFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        CacheFileRoot root = new CacheFileRoot();
        root.cacheVersion = config.getCacheVersion();
        root.chunks = new java.util.HashMap<>();

        int serializedCount = 0;
        for (Map.Entry<String, ChunkCacheEntry> entry : cache.entrySet()) {
            try {
                root.chunks.put(entry.getKey(), toJsonEntry(entry.getValue()));
                serializedCount++;
            } catch (Exception e) {
                plugin.getLogger().warning(String.format(
                    "[ChunkCacheStore] Failed to serialize entry %s: %s",
                    entry.getKey(), e.getMessage()));
            }
        }

        try (FileWriter writer = new FileWriter(cacheFile)) {
            GSON.toJson(root, writer);
            dirtyMap.clear();
            plugin.getLogger().info(String.format(
                "Saved %d chunk cache entries (%d serialized) to %s.",
                root.chunks.size(), serializedCount, cacheFile.getName()));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save chunk cache: " + e.getMessage());
        }
    }

    /**
     * Marks a cache entry as dirty so it will be persisted in the next async flush.
     *
     * @param key the cache key ({@code worldUID:chunkX:chunkZ})
     */
    public void markDirty(String key) {
        dirtyMap.put(key, Boolean.TRUE);
    }

    /**
     * Starts the periodic async save task.
     *
     * <p>Runs every {@code saveIntervalSeconds} (from config). Only entries
     * marked dirty are written; clean entries are left untouched in the file.</p>
     */
    public void startAsyncSaveTask() {
        long intervalTicks = config.getCacheSaveIntervalSeconds() * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dirtyMap.isEmpty()) return;

                // Reload existing file to merge with dirty entries
                CacheFileRoot root = loadExistingOrNew();

                int dirtyCount = 0;
                for (String dirtyKey : dirtyMap.keySet()) {
                    ChunkCacheEntry cacheEntry = cache.get(dirtyKey);
                    if (cacheEntry != null) {
                        root.chunks.put(dirtyKey, toJsonEntry(cacheEntry));
                        dirtyCount++;
                    }
                }

                if (dirtyCount > 0) {
                    root.cacheVersion = config.getCacheVersion();
                    try (FileWriter writer = new FileWriter(cacheFile)) {
                        GSON.toJson(root, writer);
                        dirtyMap.clear();
                    } catch (IOException e) {
                        plugin.getLogger().severe("Async chunk cache save failed: " + e.getMessage());
                    }
                } else {
                    dirtyMap.clear();
                }
            }
        }.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    /**
     * Loads the existing cache file, or returns a fresh root if it doesn't exist.
     */
    private CacheFileRoot loadExistingOrNew() {
        if (!cacheFile.exists()) {
            return new CacheFileRoot();
        }
        try (FileReader reader = new FileReader(cacheFile)) {
            CacheFileRoot root = GSON.fromJson(reader, CacheFileRoot.class);
            return root != null ? root : new CacheFileRoot();
        } catch (IOException e) {
            return new CacheFileRoot();
        }
    }

    /* ---------- serialization helpers ---------- */

    /**
     * Converts a {@link ChunkCacheEntry} to its JSON-serializable form.
     */
    private static ChunkJsonEntry toJsonEntry(ChunkCacheEntry entry) {
        ChunkJsonEntry json = new ChunkJsonEntry();
        json.snowCapable = entry.snowCapable;
        json.snowCovered = entry.snowCovered;
        json.snowBelowMax = entry.snowBelowMax;
        json.tempLevelMin = entry.tempLevelMin;
        json.tempLevelMax = entry.tempLevelMax;
        json.updated = entry.lastUpdated;
        json.pluginSnow = Base64.getEncoder().encodeToString(entry.pluginSnowHeight);
        json.naturalSnow = Base64.getEncoder().encodeToString(entry.naturalSnowHeight);
        json.blockedColumns = Base64.getEncoder().encodeToString(entry.blockedColumns.toByteArray());
        json.displacedPlants = encodeShortArray(entry.displacedPlantTypes);
        return json;
    }

    /**
     * Converts a JSON entry back to a {@link ChunkCacheEntry}.
     *
     * @return the restored entry, or null if the Base64 data is corrupt
     */
    private static ChunkCacheEntry fromJsonEntry(ChunkJsonEntry json) {
        try {
            ChunkCacheEntry entry = new ChunkCacheEntry();
            entry.snowCapable = json.snowCapable;
            entry.snowCovered = json.snowCovered;
            entry.snowBelowMax = json.snowBelowMax;
            entry.tempLevelMin = json.tempLevelMin;
            entry.tempLevelMax = json.tempLevelMax;
            entry.lastUpdated = json.updated;

            byte[] pluginBytes = Base64.getDecoder().decode(json.pluginSnow);
            byte[] naturalBytes = Base64.getDecoder().decode(json.naturalSnow);

            if (pluginBytes.length != ChunkCacheEntry.TOTAL_COLUMNS
                || naturalBytes.length != ChunkCacheEntry.TOTAL_COLUMNS) {
                return null;
            }

            System.arraycopy(pluginBytes, 0, entry.pluginSnowHeight, 0, ChunkCacheEntry.TOTAL_COLUMNS);
            System.arraycopy(naturalBytes, 0, entry.naturalSnowHeight, 0, ChunkCacheEntry.TOTAL_COLUMNS);

            // Restore blockedColumns from Base64-encoded BitSet
            if (json.blockedColumns != null && !json.blockedColumns.isEmpty()) {
                try {
                    byte[] blockedBytes = Base64.getDecoder().decode(json.blockedColumns);
                    entry.blockedColumns.clear();
                    for (int i = 0; i < blockedBytes.length * 8 && i < ChunkCacheEntry.TOTAL_COLUMNS; i++) {
                        if ((blockedBytes[i / 8] & (1 << (i % 8))) != 0) {
                            entry.blockedColumns.set(i);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Corrupt blockedColumns data – leave BitSet empty
                }
            }

            // Restore displacedPlantTypes from Base64-encoded short[]
            if (json.displacedPlants != null && !json.displacedPlants.isEmpty()) {
                try {
                    byte[] raw = Base64.getDecoder().decode(json.displacedPlants);
                    short[] types = decodeShortArray(raw);
                    if (types != null && types.length == ChunkCacheEntry.TOTAL_COLUMNS) {
                        System.arraycopy(types, 0, entry.displacedPlantTypes, 0, ChunkCacheEntry.TOTAL_COLUMNS);
                    }
                } catch (IllegalArgumentException e) {
                    // Corrupt displacedPlants data – leave array at zeros
                }
            }

            return entry;
        } catch (IllegalArgumentException e) {
            return null; // Corrupt Base64
        }
    }

    /* ---------- JSON data transfer objects ---------- */

    /**
     * Root object for the cache JSON file.
     */
    static class CacheFileRoot {
        int cacheVersion = 1;
        Map<String, ChunkJsonEntry> chunks = new java.util.HashMap<>();
    }

    /**
     * Per-chunk JSON entry containing all persisted fields.
     */
    static class ChunkJsonEntry {
        int snowCapable;
        int snowCovered;
        int snowBelowMax;
        int tempLevelMin;
        int tempLevelMax;
        long updated;
        String pluginSnow;
        String naturalSnow;
        String blockedColumns;
        String displacedPlants;
    }

    /* ---------- short[] encoding helpers ---------- */

    private static String encodeShortArray(short[] array) {
        byte[] bytes = new byte[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            bytes[i * 2] = (byte) (array[i] >> 8);
            bytes[i * 2 + 1] = (byte) array[i];
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static short[] decodeShortArray(byte[] raw) {
        if (raw.length % 2 != 0) return null;
        short[] array = new short[raw.length / 2];
        for (int i = 0; i < array.length; i++) {
            array[i] = (short) ((raw[i * 2] << 8) | (raw[i * 2 + 1] & 0xFF));
        }
        return array;
    }
}