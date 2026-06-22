package de.ajsch.seasons.weather;

import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.season.SeasonClock;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Orchestrates snow accumulation, growth, and melting per tick.
 * Delegates all domain logic to ChunkCacheManager, SnowPlacer, SnowGrower, and SnowMelter.
 *
 * <p>Temperature-based mode switching (Fix #3):</p>
 * <ul>
 *   <li>{@code temp < freezeThreshold && world.hasStorm()} → placement or growth</li>
 *   <li>{@code temp >= meltThreshold} → melting (regardless of weather)</li>
 *   <li>Otherwise → nothing</li>
 * </ul>
 */
public class SnowAccumulator {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final WeatherConfig weatherConfig;
    private final int scanInterval;
    private final int maxChunksPerTick;
    private final int meltChunksPerTick;
    private final ChunkCacheManager cacheManager;
    private final SnowPlacer snowPlacer;
    private final SnowGrower snowGrower;
    private final SnowMelter snowMelter;

    /** Public counters for summary logging, read and reset by the tick-loop. */
    int totalMelted = 0;
    int totalGrown = 0;
    int fullyGrownSkipped = 0;
    int totalSkippedOceans = 0;

    public SnowAccumulator(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                           WeatherConfig weatherConfig, int scanInterval, int maxChunksPerTick,
                           ChunkCacheManager cacheManager) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.weatherConfig = weatherConfig;
        this.scanInterval = scanInterval;
        this.maxChunksPerTick = maxChunksPerTick;
        this.meltChunksPerTick = weatherConfig.getMeltChunksPerTick();
        this.cacheManager = cacheManager;

        // Cast to SnowGrower/SnowMelter which take ChunkCacheStore
        this.snowPlacer = new SnowPlacer(plugin, clock, tempCalc, weatherConfig, cacheManager);
        this.snowGrower = new SnowGrower(plugin, clock, tempCalc, weatherConfig, cacheManager.getChunkCacheStore());
        this.snowMelter = new SnowMelter(plugin, clock, tempCalc, weatherConfig, cacheManager.getChunkCacheStore());
    }

    /* ---------- public API ---------- */

    public void invalidateChunk(Chunk chunk) {
        cacheManager.invalidate(ChunkCacheManager.buildCacheKey(chunk));
    }

    public SnowGrower getSnowGrower() {
        return snowGrower;
    }

    public ChunkCacheEntry getCachedEntry(Chunk chunk) {
        return cacheManager.get(ChunkCacheManager.buildCacheKey(chunk));
    }

    /* ---------- tick loop ---------- */

    private int scanCounter = 0;
    private int chunkIndex = 0;

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!weatherConfig.isEnabled()) return;

                for (World world : plugin.getServer().getWorlds()) {
                    if (world.getEnvironment() != World.Environment.NORMAL) continue;
                    processWorld(world);
                }

                scanCounter++;
                int summaryInterval = weatherConfig.getSummaryIntervalScans();
                if (summaryInterval > 0 && scanCounter % summaryInterval == 0) {
                    plugin.getLogger().info(String.format(
                        "[SnowAcc] summary: placed=%d grown=%d melted=%d | cache: %d hits, %d misses, %d fullyGrown",
                        snowPlacer.totalPlaced, totalGrown, totalMelted,
                        cacheManager.getCacheHits(), cacheManager.getCacheMisses(), fullyGrownSkipped));
                    snowPlacer.totalPlaced = 0;
                    totalGrown = 0;
                    totalMelted = 0;
                    totalSkippedOceans = 0;
                    cacheManager.resetCacheCounters();
                    fullyGrownSkipped = 0;
                }
            }
        }.runTaskTimer(plugin, scanInterval, scanInterval);
    }

    /* ---------- per-world processing ---------- */

    private void processWorld(World world) {
        Chunk[] allChunks = world.getLoadedChunks();
        if (allChunks.length == 0) return;

        double freezeThreshold = weatherConfig.getFreezeThreshold();
        double meltThreshold = weatherConfig.getMeltThreshold();
        boolean hasStorm = world.hasStorm();
        int doy = clock.calculateDayOfYear();
        int processed = 0;
        int oceansSkipped = 0;
        int grownThisTick = 0;
        int meltedThisTick = 0;
        int start = chunkIndex % allChunks.length;

        for (int i = 0; i < allChunks.length; i++) {
            int idx = (start + i) % allChunks.length;
            Chunk chunk = allChunks[idx];
            String chunkKey = ChunkCacheManager.buildCacheKey(chunk);
            ChunkCacheEntry cache = cacheManager.getOrComputeCache(chunk);

            // Ocean / all-water chunks: skip, nothing to do
            if (cache.snowCapable == 0) {
                oceansSkipped++;
                processed++;
                continue;
            }

            // Temperature-based mode selection at representative column (8,8)
            int wx = chunk.getX() * 16 + 8;
            int wz = chunk.getZ() * 16 + 8;
            Biome biome = world.getHighestBlockAt(wx, wz).getBiome();
            double temp = tempCalc.calculate(doy, biome);

            // Mode 1: Freeze — place or grow (only during storm)
            if (temp < freezeThreshold && hasStorm) {
                if (processed >= maxChunksPerTick) break;
                processed++;

                if (!cache.isSaturated(weatherConfig.getSaturationThreshold())) {
                    // Placement: fill empty columns
                    TickDiagnostics.ChunkDiag diag = new TickDiagnostics().getOrCreate(chunkKey, "placed");
                    snowPlacer.processChunk(chunk, cache, diag);
                } else {
                    // Growth: grow existing snow on saturated chunks
                    TickDiagnostics.ChunkDiag diag = new TickDiagnostics().getOrCreate(chunkKey, "saturated");
                    int grown = snowGrower.growSnowInChunk(chunk, cache, diag, chunkKey);
                    totalGrown += grown;
                    snowPlacer.totalPlaced += grown;
                    grownThisTick += grown;
                }
                if (cache.isFullyGrown()) {
                    fullyGrownSkipped++;
                }
            }
            // Mode 2: Melt — remove plugin snow (regardless of weather)
            else if (temp >= meltThreshold) {
                if (processed >= meltChunksPerTick) break;
                processed++;
                snowMelter.processMeltChunk(chunk, cache, chunkKey);
                meltedThisTick++;
            }
            // Mode 3: Nothing — cold but no storm, or mild temperature
            else {
                processed++;
            }
        }

        chunkIndex = (chunkIndex + processed) % allChunks.length;
        totalSkippedOceans += oceansSkipped;
        totalMelted += meltedThisTick;
    }
}