package de.ajsch.seasons.weather;

import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.season.SeasonClock;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Handles snow placement logic for the Seasons plugin.
 *
 * <p>Extracted from {@link SnowAccumulator} as part of Phase 1.5 refactoring.
 * Contains {@link #processChunk(Chunk, ChunkCacheEntry, TickDiagnostics.ChunkDiag, List)},
 * {@link #tryPlaceColumn(int, int, World, int)}, and all column-evaluation helpers.</p>
 *
 * <p>Plants displaced by snow placement are recorded in {@link ChunkCacheEntry#displacedPlants}
 * for later restoration by {@code SnowMelter}.</p>
 */
public class SnowPlacer {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final WeatherConfig weatherConfig;
    private final ChunkCacheManager cacheManager;

    /** Accumulated placement counter, read and reset by the owning SnowAccumulator. */
    int totalPlaced;

    private final java.util.Random random = new java.util.Random();

    public SnowPlacer(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                      WeatherConfig weatherConfig, ChunkCacheManager cacheManager) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.weatherConfig = weatherConfig;
        this.cacheManager = cacheManager;
    }

    /**
     * Places new snow on columns that currently have zero snow (both plugin and natural).
     * Uses the cached {@link ChunkCacheEntry} to skip already-snow-covered columns instantly.
     * Limited to {@code layersPerScan} placements per call.
     *
     * @param chunk         the target chunk
     * @param cache         the cached column data for this chunk
     * @param diag          per-chunk diagnostics to populate
     * @param removedPlants output list filled with plants that were removed to place snow
     * @return number of snow layers placed
     */
    public int processChunk(Chunk chunk, ChunkCacheEntry cache, TickDiagnostics.ChunkDiag diag) {
        World world = chunk.getWorld();
        int cx = chunk.getX() * 16;
        int cz = chunk.getZ() * 16;
        int doy = clock.calculateDayOfYear();

        int layersPerScan = weatherConfig.getLayersPerScan();
        int placed = 0, noGround = 0, notCapable = 0, tooWarm = 0, blocked = 0, alreadySnow = 0;
        LinkedHashSet<String> blockedSamples = new LinkedHashSet<>();

        // Build list of columns eligible for placement: no snow yet AND not permanently blocked
        List<int[]> eligible = new ArrayList<>(256);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int idx = ChunkCacheEntry.columnIndex(bx, bz);
                if (cache.pluginSnowHeight[idx] == 0 && cache.naturalSnowHeight[idx] == 0
                    && !cache.blockedColumns.get(idx)) {
                    eligible.add(new int[]{cx + bx, cz + bz, idx});
                }
            }
        }
        diag.eligibleCount = eligible.size();
        Collections.shuffle(eligible, random);

        for (int[] col : eligible) {
            if (placed >= layersPerScan) break;

            int result = tryPlaceColumn(col[0], col[1], world, doy, col[2], cache);
            switch (result) {
                case 1:
                    placed++;
                    cache.pluginSnowHeight[col[2]] = 1;
                    cache.snowCovered++;
                    cache.totalPluginSnowColumns++;
                    break;
                case -1: noGround++; break;
                case -2: notCapable++; break;
                case -3: tooWarm++; break;
                case -4:
                    blocked++;
                    if (blockedSamples.size() < 5) {
                        Block blockedTop = world.getHighestBlockAt(col[0], col[1]);
                        if (blockedTop != null) {
                            blockedSamples.add(blockedTop.getType().name());
                        }
                    }
                    break;
                case -5: alreadySnow++; break;
                default:
                    plugin.getLogger().warning(String.format(
                        "[SnowPlacer] Unexpected result=%d at %d,%d in chunk %d,%d",
                        result, col[0], col[1], chunk.getX(), chunk.getZ()));
                    break;
            }
        }

        // Transfer to diagnostics
        diag.placed = placed;
        diag.noGround = noGround;
        diag.notCapable = notCapable;
        diag.tooWarm = tooWarm;
        diag.blocked = blocked;
        diag.alreadySnow = alreadySnow;
        if (!blockedSamples.isEmpty()) {
            diag.staleCoordSamples.addAll(blockedSamples);
        }

        // Mark chunk dirty if we placed anything (so ChunkCacheStore persists it)
        if (placed > 0) {
            cacheManager.markDirty(ChunkCacheManager.buildCacheKey(chunk));
        }

        // Log every chunk where we actually placed snow
        if (placed > 0) {
            plugin.getLogger().info(String.format(
                "[SnowPlacer] chunk %d,%d placed=%d noGround=%d notCapable=%d tooWarm=%d blocked=%d alreadySnow=%d | cap=%d cov=%d eligible=%d",
                chunk.getX(), chunk.getZ(), placed, noGround, notCapable, tooWarm, blocked, alreadySnow,
                cache.snowCapable, cache.snowCovered, eligible.size()));
        }

        // Log per-chunk when anomalies present
        if (blocked > 0 || alreadySnow > 0 || (eligible.size() > 0 && placed == 0)) {
            String sampleStr = blockedSamples.isEmpty() ? "" : " samples=" + String.join(",", blockedSamples);
            plugin.getLogger().info(String.format(
                "[SnowPlacer] chunk %d,%d placed=%d noGround=%d notCapable=%d tooWarm=%d blocked=%d alreadySnow=%d | cap=%d cov=%d eligible=%d%s",
                chunk.getX(), chunk.getZ(), placed, noGround, notCapable, tooWarm, blocked, alreadySnow,
                cache.snowCapable, cache.snowCovered, eligible.size(), sampleStr));
        }

        return placed;
    }

    /**
     * Try to place new snow on this column (Case 2+3 from processColumn).
     * @return 1=placed, -1=noGround, -2=notCapable, -3=tooWarm,
     *         -4=blocked (non-replaceable block above ground),
     *         -5=alreadySnow (top is already snow/snow_block)
     */
    private int tryPlaceColumn(int wx, int wz, World world, int dayOfYear, int idx, ChunkCacheEntry cache) {
        Block top = world.getHighestBlockAt(wx, wz);
        if (top == null) return -1;

        Material topMat = top.getType();
        double temp = tempCalc.calculate(dayOfYear, top.getBiome());
        if (temp >= weatherConfig.getFreezeThreshold()) return -3;

        // Skip columns that already have snow
        if (topMat == Material.SNOW || topMat == Material.SNOW_BLOCK) return -5;

        // Case 2: Top block is a plant/fungus – remove and find ground below
        if (isReplaceablePlant(topMat)) {
            removePlantAt(top);
            cache.markDisplacedPlant(idx, topMat);
            // Always log for diagnostics
            plugin.getLogger().info(String.format(
                "[SnowPlacer] displaced plant %s at %d,%d (top) idx=%d",
                topMat.name(), wx, wz, idx));
            // Fall through to case 3: find solid ground and place
        }

        // Case 3: Top block is something else – check if snow-capable solid ground exists
        Block ground = getGroundBlock(world, wx, wz);
        if (ground == null) return -1;

        // Only place on full blocks (no fences, walls, glass panes, torches)
        if (!isSnowCapable(ground)) return -2;

        Block above = ground.getRelative(BlockFace.UP);
        Material aboveMat = above.getType();

        // Don't overwrite existing snow
        if (aboveMat == Material.SNOW || aboveMat == Material.SNOW_BLOCK) return -5;

        // Don't place on non-air, non-plant blocks
        if (!above.isEmpty() && !isReplaceablePlant(aboveMat)) return -4;

        if (isReplaceablePlant(aboveMat)) {
            removePlantAt(above);
            cache.markDisplacedPlant(idx, aboveMat);
            // Always log for diagnostics
            plugin.getLogger().info(String.format(
                "[SnowPlacer] displaced plant %s at %d,%d (above) idx=%d",
                aboveMat.name(), wx, wz, idx));
        }

        Snow snowData = (Snow) Material.SNOW.createBlockData();
        snowData.setLayers(1);
        above.setBlockData(snowData, false);
        totalPlaced++;
        return 1;
    }

    /**
     * Returns the solid ground block at world coordinates (wx, wz) using HeightMap.
     * MOTION_BLOCKING gives the highest solid/water block; if that block is not solid
     * (e.g. water, plant), we step down one block to find the ground.
     *
     * @return the ground block, or null if the chunk is not loaded or no solid ground exists
     */
    private Block getGroundBlock(World world, int wx, int wz) {
        int cx = wx >> 4;
        int cz = wz >> 4;
        if (!world.isChunkLoaded(cx, cz)) return null;
        Chunk chunk = world.getChunkAt(cx, cz);
        int lx = wx & 15;
        int lz = wz & 15;
        int topY = world.getHighestBlockYAt(wx, wz, org.bukkit.HeightMap.MOTION_BLOCKING);
        Block topBlock = chunk.getBlock(lx, topY, lz);
        if (topBlock.getType() == Material.WATER || topBlock.getType() == Material.LAVA) {
            return null;
        }
        if (topBlock.getType().isSolid()) {
            return topBlock;
        }
        // Fallback: the top block is non-solid (plant, etc.) – ground is one below
        if (topY > chunk.getWorld().getMinHeight()) {
            Block below = chunk.getBlock(lx, topY - 1, lz);
            if (below.getType().isSolid()) return below;
        }
        return null;
    }

    /**
     * Returns true if this block can hold snow: a full block (not fence, wall, pane, torch).
     */
    private boolean isSnowCapable(Block block) {
        return block.getType().isSolid() && block.getType().isBlock() && block.getBoundingBox().getHeight() >= 1.0;
    }

    /**
     * Returns true if the space above ground is placeable for snow.
     * Acceptable: air, cave_air, void_air, or a replaceable plant.
     * Not acceptable: already snow/snow_block, torch, slab, carpet, or any solid block.
     */
    private boolean isColumnPlaceable(Block aboveGround) {
        Material mat = aboveGround.getType();
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return true;
        if (mat == Material.SNOW || mat == Material.SNOW_BLOCK) return true; // already snow
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


    private void removePlantAt(Block plant) {
        Material type = plant.getType();
        if (weatherConfig.getDoublePlants().contains(type)) {
            // Remove both halves: the one above is also the same Material
            Block above = plant.getRelative(0, 1, 0);
            if (above.getType() == type) {
                above.setType(Material.AIR, false);
            }
        }
        plant.setType(Material.AIR, false);
    }
}