package de.ajsch.seasons.weather;

import de.ajsch.seasons.persistence.ChunkCacheStore;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * Handles snow melting on non-winter seasons using the chunk cache.
 *
 * <p>Extracted from {@link SnowAccumulator} as part of Phase 1.5 (Sprint 1.5.5).
 * Operates exclusively on {@link ChunkCacheEntry#pluginSnowHeight} – natural snow
 * is never touched. Cache counters are updated so the cache stays consistent.</p>
 *
 * <p><b>Plant restoration:</b> When {@code pluginSnowHeight[idx]} reaches 0 and
 * {@link ChunkCacheEntry#hasDisplacedPlant(int)} returns true, the plant is restored
 * (default: SHORT_GRASS). The flag is cleared after restoration.</p>
 */
public class SnowMelter {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final WeatherConfig weatherConfig;
    private final ChunkCacheStore chunkCacheStore;
    private final Random random = new Random();

    public SnowMelter(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                      WeatherConfig weatherConfig, ChunkCacheStore chunkCacheStore) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.weatherConfig = weatherConfig;
        this.chunkCacheStore = chunkCacheStore;
    }

    /**
     * Melts plugin snow from the given chunk.
     */
    public void processMeltChunk(Chunk chunk, ChunkCacheEntry cache, String chunkKey) {
        if (cache.totalPluginSnowColumns <= 0) return;

        int cx = chunk.getX() * 16;
        int cz = chunk.getZ() * 16;
        int doy = clock.calculateDayOfYear();
        double meltThreshold = weatherConfig.getMeltThreshold();
        int meltSpeed = weatherConfig.getMeltSpeed();
        int meltLayersPerChunk = weatherConfig.getMeltLayersPerChunk();

        // Diagnose: Wie viele displaced-Plant-Einträge hat dieser Cache?
        int displacedBefore = 0;
        for (int i = 0; i < 256; i++) {
            if (cache.displacedPlantTypes[i] != 0) displacedBefore++;
        }

        List<int[]> toMelt = new ArrayList<>(256);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int idx = ChunkCacheEntry.columnIndex(bx, bz);
                if (cache.pluginSnowHeight[idx] > 0) {
                    toMelt.add(new int[]{cx + bx, cz + bz, idx});
                }
            }
        }

        if (toMelt.isEmpty()) {
            cache.totalPluginSnowColumns = 0;
            return;
        }

        Collections.shuffle(toMelt, random);

        // --- Diagnose-Zähler (pro Chunk) ---
        int melted = 0;
        int skipTemp = 0;
        int syncFoundTop = 0;
        int syncFoundAbove = 0;
        int syncNotFound = 0;
        int displacedFound = 0;  // Anzahl displaced != null
        int restored = 0;        // davon erfolgreich restored
        int displacedNull = 0;    // newTotal==0 aber displaced==null
        LinkedHashSet<String> syncFailSamples = new LinkedHashSet<>();

        for (int[] col : toMelt) {
            if (melted >= meltLayersPerChunk) break;

            int wx = col[0];
            int wz = col[1];
            int idx = col[2];

            Block highest = chunk.getWorld().getHighestBlockAt(wx, wz);
            if (highest == null) continue;
            double temp = tempCalc.calculate(doy, highest.getBiome());
            if (temp < meltThreshold) {
                skipTemp++;
                continue;
            }

            byte currentPlugin = cache.pluginSnowHeight[idx];
            int removed = Math.min(meltSpeed, currentPlugin);

            cache.pluginSnowHeight[idx] = (byte) (currentPlugin - removed);
            if (cache.pluginSnowHeight[idx] == 0) {
                cache.totalPluginSnowColumns--;
            }

            int newTotal = cache.naturalSnowHeight[idx] + cache.pluginSnowHeight[idx];
            SyncResult sr = syncColumn(chunk, wx - cx, wz - cz, newTotal);
            if (sr.foundAtTop) {
                syncFoundTop++;
            } else if (sr.foundAboveGround) {
                syncFoundAbove++;
            } else {
                syncNotFound++;
                if (syncFailSamples.size() < 5) {
                    syncFailSamples.add(String.format("%d,%d newTotal=%d highest=%s(%d)",
                        wx, wz, newTotal,
                        (sr.highestType != null ? sr.highestType : "null"),
                        sr.highestY));
                }
            }

            // Plant restoration: if snow is gone and a plant was displaced here
            if (newTotal == 0) {
                Material displacedType = cache.getDisplacedPlantType(idx);
                if (displacedType != null) {
                    displacedFound++;
                    restorePlantAt(chunk, wx - cx, wz - cz, displacedType);
                    cache.clearDisplacedPlant(idx);
                    restored++;
                    // Always log for diagnostics
                    plugin.getLogger().info(String.format(
                        "[SnowMelter] restored plant %s at %d,%d idx=%d",
                        displacedType.name(), wx, wz, idx));
                } else {
                    displacedNull++;
                }
            }

            if (newTotal == 0 && cache.naturalSnowHeight[idx] == 0) {
                if (cache.snowCovered > 0) cache.snowCovered--;
            }

            melted++;
        }

        // Diagnose: Wie viele displaced-Plant-Einträge hat dieser Cache jetzt?
        int displacedAfter = 0;
        for (int i = 0; i < 256; i++) {
            if (cache.displacedPlantTypes[i] != 0) displacedAfter++;
        }

        // --- Diagnose-Log pro Chunk ---
        if (melted > 0) {
            plugin.getLogger().info(String.format(
                "[SnowMelter] chunk %d,%d melted=%d skipTemp=%d foundTop=%d foundAbove=%d notFound=%d | displBefore=%d displFound=%d restored=%d displNull=%d displAfter=%d | totalPlugin=%d%s",
                chunk.getX(), chunk.getZ(), melted, skipTemp, syncFoundTop, syncFoundAbove,
                syncNotFound, displacedBefore, displacedFound, restored, displacedNull, displacedAfter,
                cache.totalPluginSnowColumns,
                syncFailSamples.isEmpty() ? "" : " samples=" + String.join(",", syncFailSamples)));
        }

        if (melted > 0 && chunkCacheStore != null) {
            chunkCacheStore.markDirty(chunkKey);
        }
    }

    /** Holds the result of a syncColumn for diagnostics. */
    private static class SyncResult {
        boolean foundAtTop = false;
        boolean foundAboveGround = false;
        String highestType = null;
        int highestY = -1;
    }

    /**
     * Synchronizes the physical world with cache state.
     */
    private SyncResult syncColumn(Chunk chunk, int lx, int lz, int newTotal) {
        SyncResult result = new SyncResult();
        int wx = chunk.getX() * 16 + lx;
        int wz = chunk.getZ() * 16 + lz;
        Block highest = chunk.getWorld().getHighestBlockAt(wx, wz);
        if (highest == null) return result;

        result.highestType = highest.getType().name();
        result.highestY = highest.getY();

        if (newTotal == 0) {
            // Remove all snow from this column
            if (highest.getType() == Material.SNOW) {
                highest.setType(Material.AIR, false);
                result.foundAtTop = true;
            } else if (highest.getType() == Material.SNOW_BLOCK) {
                highest.setType(Material.AIR, false);
                result.foundAtTop = true;
                Block below = highest.getRelative(0, -1, 0);
                if (below.getType() == Material.SNOW_BLOCK || below.getType() == Material.SNOW) {
                    below.setType(Material.AIR, false);
                }
            }
            // Fallback: getHighestBlockAt() may return ground instead of snow above it
            if (!result.foundAtTop) {
                Block aboveGround = chunk.getWorld().getBlockAt(wx, highest.getY() + 1, wz);
                if (aboveGround.getType() == Material.SNOW) {
                    aboveGround.setType(Material.AIR, false);
                    result.foundAboveGround = true;
                } else if (aboveGround.getType() == Material.SNOW_BLOCK) {
                    aboveGround.setType(Material.AIR, false);
                    result.foundAboveGround = true;
                    Block twoAbove = chunk.getWorld().getBlockAt(wx, highest.getY() + 2, wz);
                    if (twoAbove.getType() == Material.SNOW || twoAbove.getType() == Material.SNOW_BLOCK) {
                        twoAbove.setType(Material.AIR, false);
                    }
                }
            }
        } else if (newTotal >= 8) {
            // Convert to full snow block
            if (highest.getType() == Material.SNOW || highest.getType() == Material.SNOW_BLOCK) {
                highest.setType(Material.SNOW_BLOCK, false);
                result.foundAtTop = true;
            } else {
                Block target = chunk.getWorld().getBlockAt(wx, highest.getY() + 1, wz);
                if (target.isEmpty() || target.getType() == Material.SNOW || target.getType() == Material.SNOW_BLOCK) {
                    target.setType(Material.SNOW_BLOCK, false);
                    result.foundAboveGround = true;
                }
            }
        } else {
            // Set snow layer with newTotal layers
            if (highest.getType() == Material.SNOW) {
                Snow snow = (Snow) highest.getBlockData();
                snow.setLayers(newTotal);
                highest.setBlockData(snow, false);
                result.foundAtTop = true;
            } else if (highest.getType() == Material.SNOW_BLOCK) {
                Snow snow = (Snow) Material.SNOW.createBlockData();
                snow.setLayers(newTotal);
                highest.setBlockData(snow, false);
                result.foundAtTop = true;
            } else {
                Block target = chunk.getWorld().getBlockAt(wx, highest.getY() + 1, wz);
                if (target.getType() == Material.SNOW) {
                    Snow snow = (Snow) target.getBlockData();
                    snow.setLayers(newTotal);
                    target.setBlockData(snow, false);
                    result.foundAboveGround = true;
                } else if (target.getType() == Material.SNOW_BLOCK && newTotal < 8) {
                    Snow snow = (Snow) Material.SNOW.createBlockData();
                    snow.setLayers(newTotal);
                    target.setBlockData(snow, false);
                    result.foundAboveGround = true;
                }
            }
        }
        return result;
    }

    /**
     * Restores a plant at the given column position.
     * The plant type is read from the cache (displacedPlantTypes).
     */
    private void restorePlantAt(Chunk chunk, int lx, int lz, Material plantType) {
        int wx = chunk.getX() * 16 + lx;
        int wz = chunk.getZ() * 16 + lz;
        Block highest = chunk.getWorld().getHighestBlockAt(wx, wz);
        if (highest == null) return;

        // Place on top of the highest solid block
        Block target;
        if (highest.getType().isSolid()) {
            target = highest.getRelative(BlockFace.UP);
        } else {
            target = highest;
        }

        if (target.isEmpty() || target.getType() == Material.AIR
            || target.getType() == Material.CAVE_AIR
            || target.getType() == Material.VOID_AIR) {
            target.setType(plantType, false);
        }
    }
}