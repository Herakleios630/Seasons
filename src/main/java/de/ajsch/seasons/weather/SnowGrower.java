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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Handles snow growth on already-saturated chunks.
 *
 * <p>Extracted from {@link SnowAccumulator} as part of Phase 1.5 (Sprint 1.5.4).
 * Contains {@link #growSnowInChunk(Chunk, ChunkCacheEntry, TickDiagnostics.ChunkDiag, String)}
 * and the temperature-based max-height calculator {@link #getMaxSnowHeight(double)}.</p>
 */
public class SnowGrower {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final WeatherConfig weatherConfig;
    private final ChunkCacheStore chunkCacheStore;
    private final Random random = new Random();

    public SnowGrower(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                      WeatherConfig weatherConfig, ChunkCacheStore chunkCacheStore) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.weatherConfig = weatherConfig;
        this.chunkCacheStore = chunkCacheStore;
    }

    /**
     * Grows existing snow on saturated chunks (where every snow-capable column already has
     * at least one snow layer, natural or plugin-managed). Picks columns with any snow,
     * shuffles them, and increases their snow layer by 1.
     * Columns that previously only had natural snow are adopted as plugin-managed on first touch.
     * Limited to {@code growthLayersPerScan} placements per call.
     *
     * @param chunk     the chunk to grow snow in
     * @param cache     the pre-computed cache entry for this chunk
     * @param diag      per-chunk diagnostics to populate
     * @param chunkKey  cache key for markDirty
     * @return the number of snow layers grown
     */
    public int growSnowInChunk(Chunk chunk, ChunkCacheEntry cache, TickDiagnostics.ChunkDiag diag, String chunkKey) {
        // Saturation guard: only grow on fully saturated chunks
        if (!cache.isSaturated(weatherConfig.getSaturationThreshold())) {
            return 0;
        }

        World world = chunk.getWorld();
        int cx = chunk.getX() * 16;
        int cz = chunk.getZ() * 16;
        int doy = clock.calculateDayOfYear();
        int growthPerScan = weatherConfig.getGrowthLayersPerScan();

        int skippedTotalZero = 0, skippedTempLimit = 0, skippedAlreadyMax = 0, skippedSnowBlock = 0;
        int staleResets = 0;

        // Collect columns with any snow (plugin or natural) that are below temperature-limited max height
        List<int[]> growable = new ArrayList<>(256);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int idx = ChunkCacheEntry.columnIndex(bx, bz);
                int totalCurrent = cache.pluginSnowHeight[idx] + cache.naturalSnowHeight[idx];
                if (totalCurrent == 0) {
                    skippedTotalZero++;
                    continue;
                }
                if (totalCurrent >= 8) {
                    skippedAlreadyMax++;
                    continue;
                }
                // Check temperature limit at column center (rough, matches scan logic)
                org.bukkit.block.Biome biome = world.getHighestBlockAt(cx + bx, cz + bz).getBiome();
                double temp = tempCalc.calculate(doy, biome);
                int maxHeight = getMaxSnowHeight(temp);
                if (totalCurrent >= maxHeight) {
                    skippedTempLimit++;
                    continue;
                }
                growable.add(new int[]{cx + bx, cz + bz, idx});
            }
        }

        diag.growableBefore = growable.size();

        if (growable.isEmpty()) {
            diag.skippedTotalZero = skippedTotalZero;
            diag.skippedTempLimit = skippedTempLimit;
            diag.skippedAlreadyMax = skippedAlreadyMax;
            diag.skippedSnowBlock = skippedSnowBlock;
            return 0;
        }

        Collections.shuffle(growable, random);
        int grown = 0;

        for (int[] col : growable) {
            if (grown >= growthPerScan) break;

            int wx = col[0];
            int wz = col[1];
            int idx = col[2];

            Block top = world.getHighestBlockAt(wx, wz);
            if (top == null) continue;

            // getHighestBlockAt() uses WORLD_SURFACE which ignores thin snow layers (1/8 block).
            // If the top block is solid ground and the block above it is snow, use that instead.
            Block snowBlock = null;
            if (top.getType() == Material.SNOW || top.getType() == Material.SNOW_BLOCK) {
                snowBlock = top;
            } else if (top.getType().isSolid()) {
                Block above = top.getRelative(BlockFace.UP);
                if (above.getType() == Material.SNOW || above.getType() == Material.SNOW_BLOCK) {
                    snowBlock = above;
                }
            }

            if (snowBlock == null) {
                // No snow physically present – cache is stale; correct counters and skip
                staleResets++;
                if (staleResets <= 3) {
                    diag.staleCoordSamples.add(String.format("%d,%d:%s", wx, wz, top.getType().name()));
                }
                if (cache.pluginSnowHeight[idx] > 0 || cache.naturalSnowHeight[idx] > 0) {
                    if (cache.snowCovered > 0) cache.snowCovered--;
                }
                if (cache.pluginSnowHeight[idx] > 0) {
                    if (cache.totalPluginSnowColumns > 0) cache.totalPluginSnowColumns--;
                }
                cache.pluginSnowHeight[idx] = 0;
                cache.naturalSnowHeight[idx] = 0;
                continue;
            }
            // If this column had only natural snow before, mark it as plugin-managed now
            boolean justAdopted = (cache.pluginSnowHeight[idx] == 0);
            if (justAdopted) {
                cache.pluginSnowHeight[idx] = 1;
                cache.totalPluginSnowColumns++;
            }

            // SNOW_BLOCK is always max height – cannot grow further
            if (snowBlock.getType() == Material.SNOW_BLOCK) {
                skippedSnowBlock++;
                continue;
            }

            org.bukkit.block.Biome biome = snowBlock.getBiome();
            double temp = tempCalc.calculate(doy, biome);
            int maxHeight = getMaxSnowHeight(temp);
            int totalCurrent = cache.pluginSnowHeight[idx] + cache.naturalSnowHeight[idx];

            // Only grow if below temperature-limited max
            if (totalCurrent >= maxHeight) {
                skippedTempLimit++;
                if (cache.snowBelowMax > 0) {
                    cache.snowBelowMax--;
                }
                continue;
            }

            Snow snow = (Snow) snowBlock.getBlockData();
            int layers = snow.getLayers();
            int limit = Math.min(maxHeight, snow.getMaximumLayers());

            if (layers >= limit) {
                // Convert to snow block and place new layer above
                Block above = snowBlock.getRelative(BlockFace.UP);
                if (above.isEmpty()) {
                    snowBlock.setType(Material.SNOW_BLOCK, false);
                    Snow newLayer = (Snow) Material.SNOW.createBlockData();
                    newLayer.setLayers(1);
                    above.setBlockData(newLayer, false);
                }
            } else {
                snow.setLayers(layers + 1);
                snowBlock.setBlockData(snow, false);
            }
            grown++;

            // Update cache height: only increment if not freshly adopted
            if (!justAdopted) {
                cache.pluginSnowHeight[idx]++;
            }

            // Update snowBelowMax when this column reaches the cap
            if (totalCurrent + 1 >= maxHeight) {
                if (cache.snowBelowMax > 0) {
                    cache.snowBelowMax--;
                }
            }
        }

        // Record diagnostics
        diag.grown = grown;
        diag.staleResets = staleResets;
        diag.skippedTempLimit = skippedTempLimit;
        diag.skippedAlreadyMax = skippedAlreadyMax;
        diag.skippedSnowBlock = skippedSnowBlock;
        diag.skippedTotalZero = skippedTotalZero;

        // Mark chunk dirty if we grew anything (so ChunkCacheStore persists it)
        if (grown > 0 && chunkCacheStore != null) {
            chunkCacheStore.markDirty(chunkKey);
        }

        if (grown > 0 || staleResets > 0) {
            plugin.getLogger().info(String.format(
                "[SnowGrower] chunk %d,%d grown=%d stale=%d growableB4=%d | cap=%d cov=%d belowMax=%d",
                chunk.getX(), chunk.getZ(), grown, staleResets, diag.growableBefore,
                cache.snowCapable, cache.snowCovered, cache.snowBelowMax));
        }

        return grown;
    }

    /**
     * Calculates the maximum snow height for a given temperature.
     * Colder temperatures allow higher snow stacks.
     */
    public int getMaxSnowHeight(double temperature) {
        int base = weatherConfig.getMaxNaturalHeight();
        if (temperature >= weatherConfig.getFreezeThreshold()) return base;
        int extra = (int) ((weatherConfig.getFreezeThreshold() - temperature) / 0.2)
                    * weatherConfig.getHeightPerCold();
        return base + extra;
    }
}