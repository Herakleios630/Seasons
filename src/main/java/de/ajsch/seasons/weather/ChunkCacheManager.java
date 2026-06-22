package de.ajsch.seasons.weather;

import de.ajsch.seasons.persistence.ChunkCacheStore;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the per-chunk snow cache: lookup, scanning, invalidation, and TTL-based freshness.
 *
 * <p>Extracted from {@link SnowAccumulator} as part of Phase 1.5 refactoring.
 * Contains the cache map, {@link #getOrComputeCache(Chunk)}, {@link #scanChunkColumns(Chunk, ChunkCacheEntry)},
 * and related helpers. Also includes Fix #1 (cache drift) and Fix #5 (markDirty after scan).</p>
 */
public class ChunkCacheManager {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final WeatherConfig weatherConfig;
    private ChunkCacheStore chunkCacheStore;

    /**
     * Returns the ChunkCacheStore reference (may be null before setChunkCacheStore is called).
     */
    public ChunkCacheStore getChunkCacheStore() {
        return chunkCacheStore;
    }

    private final ConcurrentHashMap<String, ChunkCacheEntry> chunkCache = new ConcurrentHashMap<>();

    /** Summary counters for cache performance logging */
    private int cacheHits = 0;
    private int cacheMisses = 0;

    public ChunkCacheManager(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                             WeatherConfig weatherConfig, ChunkCacheStore chunkCacheStore) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.weatherConfig = weatherConfig;
        this.chunkCacheStore = chunkCacheStore;
    }

    /* ---------- cache access ---------- */

    /**
     * Returns direct access to the chunk cache map for persistence.
     */
    public ConcurrentHashMap<String, ChunkCacheEntry> getCacheMap() {
        return chunkCache;
    }

    /**
     * Returns the cached entry for the given key, or null if not present.
     */
    public ChunkCacheEntry get(String key) {
        return chunkCache.get(key);
    }

    /**
     * Invalidates (removes) the cached entry for the given key.
     */
    public void invalidate(String key) {
        chunkCache.remove(key);
    }

    /**
     * Clears the entire chunk cache.
     */
    public void clearCache() {
        chunkCache.clear();
    }

    public int getCacheHits() {
        return cacheHits;
    }

    public int getCacheMisses() {
        return cacheMisses;
    }

    public void resetCacheCounters() {
        cacheHits = 0;
        cacheMisses = 0;
    }

    /**
     * Sets the ChunkCacheStore reference after construction (chicken-and-egg resolution).
     * Called from {@code SeasonsPlugin} after ChunkCacheStore is created.
     */
    public void setChunkCacheStore(ChunkCacheStore store) {
        chunkCacheStore = store;
    }

    /**
     * Marks a cache entry as dirty so it will be persisted in the next async flush.
     *
     * @param key the cache key ({@code worldUID:chunkX:chunkZ})
     */
    public void markDirty(String key) {
        if (chunkCacheStore != null) {
            chunkCacheStore.markDirty(key);
        }
    }

    /* ---------- key builders ---------- */

    /**
     * Builds the cache key for a chunk.
     * Format: {@code worldUID:chunkX:chunkZ}
     */
    public static String buildCacheKey(World world, int chunkX, int chunkZ) {
        return world.getUID().toString() + ":" + chunkX + ":" + chunkZ;
    }

    /**
     * Builds the cache key for a chunk.
     */
    public static String buildCacheKey(Chunk chunk) {
        return buildCacheKey(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /* ---------- main access method ---------- */

    /**
     * Retrieves the cached {@link ChunkCacheEntry} for the given chunk, or computes a fresh one
     * via {@link #scanChunkColumns(Chunk, ChunkCacheEntry)} if the cache is stale.
     *
     * <p>Cache validity checks:</p>
     * <ul>
     *   <li>Entry missing → MISS</li>
     *   <li>TTL expired (config-controlled) → MISS</li>
     *   <li>Otherwise → HIT</li>
     * </ul>
     */
    public ChunkCacheEntry getOrComputeCache(Chunk chunk) {
        String key = buildCacheKey(chunk);
        ChunkCacheEntry oldEntry = chunkCache.get(key);

        if (oldEntry != null) {
            long ageSeconds = (System.currentTimeMillis() - oldEntry.lastUpdated) / 1000;
            if (ageSeconds <= weatherConfig.getCacheTTLSeconds()) {
                cacheHits++;
                return oldEntry;
            }
        }

        cacheMisses++;
        ChunkCacheEntry newEntry = scanChunkColumns(chunk, oldEntry);
        chunkCache.put(key, newEntry);
        return newEntry;
    }

    /* ---------- column scanner ---------- */

    /**
     * Scans all 256 columns of a chunk and builds a fresh {@link ChunkCacheEntry}.
     * Uses {@code world.getHighestBlockAt()} for consistent snow detection.
     *
     * @param chunk    the chunk to scan
     * @param oldEntry the previous cache entry (may be null); used to preserve plugin snow attribution
     *                 and displaced plant types (they cannot be reconstructed from the world)
     * @return a fully populated ChunkCacheEntry
     */
    public ChunkCacheEntry scanChunkColumns(Chunk chunk, ChunkCacheEntry oldEntry) {
        String key = buildCacheKey(chunk); // needed for Fix #5 (markDirty)
        ChunkCacheEntry entry = new ChunkCacheEntry();
        entry.resetMetadata();

        // Preserve displaced plant types from old entry (they cannot be re-scanned)
        if (oldEntry != null) {
            System.arraycopy(oldEntry.displacedPlantTypes, 0, entry.displacedPlantTypes, 0, 256);
        }

        int dayOfYear = clock.calculateDayOfYear();
        World world = chunk.getWorld();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = ChunkCacheEntry.columnIndex(x, z);
                int wx = chunk.getX() * 16 + x;
                int wz = chunk.getZ() * 16 + z;

                // Use getHighestBlockAt (same as tryPlaceColumn) for consistent snow detection.
                // HeightMap.MOTION_BLOCKING skips thin snow layers (1/8 block).
                Block topBlock = world.getHighestBlockAt(wx, wz);
                if (topBlock == null) continue;

                boolean topIsSnow = topBlock.getType() == Material.SNOW
                                 || topBlock.getType() == Material.SNOW_BLOCK;

                // Determine ground block
                Block ground;
                if (topBlock.getType().isSolid()) {
                    ground = topBlock;
                } else if (topIsSnow) {
                    // Snow sits on ground – ground is one below
                    int groundY = topBlock.getY() - 1;
                    if (groundY >= world.getMinHeight()) {
                        ground = chunk.getBlock(x, groundY, z);
                    } else {
                        ground = null;
                    }
                } else {
                    // Non-solid top (plant, water, etc.) – ground is one below
                    int groundY = topBlock.getY() - 1;
                    if (groundY >= world.getMinHeight()) {
                        ground = chunk.getBlock(x, groundY, z);
                    } else {
                        ground = null;
                    }
                }

                // Determine the block directly above ground (or top block if no ground)
                Block aboveGround = (ground != null) ? ground.getRelative(BlockFace.UP) : topBlock;

                // Count snow-capable columns: ground must be snow-capable.
                boolean groundCapable = ground != null && isSnowCapable(ground);
                boolean columnPlaceable = groundCapable && isColumnPlaceable(aboveGround);

                if (columnPlaceable) {
                    entry.snowCapable++;
                } else if (groundCapable) {
                    // Ground is solid, but the space above is permanently blocked
                    // (e.g. torch, slab, carpet, non-replaceable plant).
                    entry.blockedColumns.set(idx);
                }

                // Determine current physical snow on this column
                int physicalSnow = 0;
                if (topIsSnow) {
                    if (topBlock.getType() == Material.SNOW) {
                        Snow snowData = (Snow) topBlock.getBlockData();
                        physicalSnow = snowData.getLayers();
                    } else {
                        physicalSnow = 8; // SNOW_BLOCK
                    }
                } else if (aboveGround.getType() == Material.SNOW) {
                    Snow snowData = (Snow) aboveGround.getBlockData();
                    physicalSnow = snowData.getLayers();
                } else if (aboveGround.getType() == Material.SNOW_BLOCK) {
                    physicalSnow = 8;
                }

                // --- Reconstruct natural vs. plugin snow heights ---
                // Fix #1: Cache Drift – always use physical snow height for plugin attribution,
                // not the stale oldPlugin value. If plugin snow was placed and is still present,
                // physicalSnow reflects reality.
                byte oldPlugin = (oldEntry != null) ? oldEntry.pluginSnowHeight[idx] : 0;

                // Calculate temperature for this column (needed for fallback classification)
                org.bukkit.block.Biome biome = topBlock.getBiome();
                double temp = tempCalc.calculate(dayOfYear, biome);

                if (physicalSnow > 0 && oldPlugin > 0) {
                    // Plugin snow still present – use actual physical height (Fix #1)
                    entry.pluginSnowHeight[idx] = (byte) physicalSnow;
                    entry.naturalSnowHeight[idx] = 0;
                    entry.totalPluginSnowColumns++;
                    entry.snowCovered++;
                } else if (physicalSnow > 0) {
                    // Physical snow exists but no plugin attribution
                    // Fallback: if temperature is at/above freezeThreshold, vanilla snow
                    // cannot form here → must be plugin snow (cache was lost, e.g. crash)
                    if (temp >= weatherConfig.getFreezeThreshold()) {
                        entry.pluginSnowHeight[idx] = (byte) physicalSnow;
                        entry.naturalSnowHeight[idx] = 0;
                        entry.totalPluginSnowColumns++;
                    } else {
                        entry.naturalSnowHeight[idx] = (byte) physicalSnow;
                        entry.pluginSnowHeight[idx] = 0;
                    }
                    entry.snowCovered++;
                } else if (oldPlugin > 0) {
                    // Plugin snow was here but melted → reset and update counters
                    entry.pluginSnowHeight[idx] = 0;
                    entry.naturalSnowHeight[idx] = 0;
                } else {
                    // No snow, no plugin history
                    entry.pluginSnowHeight[idx] = 0;
                    entry.naturalSnowHeight[idx] = 0;
                }

                int tempLevel = (int) Math.round(temp * 10); // scale to integer level

                if (tempLevel < entry.tempLevelMin) entry.tempLevelMin = tempLevel;
                if (tempLevel > entry.tempLevelMax) entry.tempLevelMax = tempLevel;

                // Check if snow is below temperature-limited max
                int maxHeight = getMaxSnowHeight(temp);
                int totalSnow = entry.pluginSnowHeight[idx] + entry.naturalSnowHeight[idx];
                if (totalSnow < maxHeight) {
                    entry.snowBelowMax++;
                }
            }
        }

        entry.lastUpdated = System.currentTimeMillis();

        // Fix #5: markDirty after scan – if this chunk has plugin snow, ensure it gets persisted
        if (entry.totalPluginSnowColumns > 0 && chunkCacheStore != null) {
            chunkCacheStore.markDirty(key);
        }

        return entry;
    }

    /* ---------- helper methods (mirrored from SnowAccumulator for scanChunkColumns) ---------- */

    /**
     * Returns true if this block can hold snow: a full solid block.
     */
    private boolean isSnowCapable(Block block) {
        return block.getType().isSolid() && block.getType().isBlock() && block.getBoundingBox().getHeight() >= 1.0;
    }

    /**
     * Returns true if the space above ground is placeable for snow.
     * Acceptable: air, cave_air, void_air, existing snow, or a replaceable plant.
     * Not acceptable: torch, slab, carpet, or any solid block.
     */
    private boolean isColumnPlaceable(Block aboveGround) {
        Material mat = aboveGround.getType();
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return true;
        if (mat == Material.SNOW || mat == Material.SNOW_BLOCK) return true;
        if (isReplaceablePlant(mat)) return true;
        return false;
    }

    /**
     * Checks if the material is a replaceable plant (grass, fern, flower, etc.).
     * Delegates to WeatherConfig / ConfigManager → replaceable_plants.yml.
     */
    private boolean isReplaceablePlant(Material mat) {
        return weatherConfig.getReplaceablePlants().contains(mat)
            || weatherConfig.getDoublePlants().contains(mat);
    }

    /**
     * Calculates the maximum snow height for a given temperature.
     */
    private int getMaxSnowHeight(double temperature) {
        int base = weatherConfig.getMaxNaturalHeight();
        if (temperature >= weatherConfig.getFreezeThreshold()) return base;
        int extra = (int) ((weatherConfig.getFreezeThreshold() - temperature) / 0.2)
                    * weatherConfig.getHeightPerCold();
        return base + extra;
    }
}