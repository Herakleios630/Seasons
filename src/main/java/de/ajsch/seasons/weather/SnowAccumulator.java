package de.ajsch.seasons.weather;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SnowAccumulator {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final WeatherConfig weatherConfig;
    private final int scanInterval;
    private final int maxChunksPerTick;

    public SnowAccumulator(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                           WeatherConfig weatherConfig, int scanInterval, int maxChunksPerTick) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.weatherConfig = weatherConfig;
        this.scanInterval = scanInterval;
        this.maxChunksPerTick = maxChunksPerTick;
    }

    private int scanCounter = 0;
    private int totalPlaced = 0;
    private int totalMelted = 0;
    private int totalSkippedOceans = 0;

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!weatherConfig.isEnabled()) return;

                for (World world : plugin.getServer().getWorlds()) {
                    if (world.getEnvironment() != World.Environment.NORMAL) continue;

                    if (clock.getCurrentSeason() == Season.WINTER) {
                        accumulateSnow(world);
                    } else {
                        meltSnow(world);
                    }
                }

                scanCounter++;
                int summaryInterval = weatherConfig.getSummaryIntervalScans();
                if (summaryInterval > 0 && scanCounter % summaryInterval == 0) {
                    plugin.getLogger().info(String.format(
                        "SnowAccumulator summary after %d scans: placed=%d melted=%d skippedOceans=%d",
                        scanCounter, totalPlaced, totalMelted, totalSkippedOceans));
                    totalPlaced = 0;
                    totalMelted = 0;
                    totalSkippedOceans = 0;
                }
            }
        }.runTaskTimer(plugin, scanInterval, scanInterval);
    }

    private int chunkIndex = 0;

    private void accumulateSnow(World world) {
        if (!world.hasStorm()) return;
        org.bukkit.Chunk[] allChunks = world.getLoadedChunks();
        if (allChunks.length == 0) return;
        int processed = 0;
        int start = chunkIndex % allChunks.length;
        for (int i = 0; i < allChunks.length && processed < maxChunksPerTick; i++) {
            int idx = (start + i) % allChunks.length;
            processChunk(allChunks[idx]);
            processed++;
        }
        chunkIndex = (chunkIndex + maxChunksPerTick) % allChunks.length;
    }

    private final java.util.Random random = new java.util.Random();

    private static final int[][] NEIGHBORS = {{1,0}, {-1,0}, {0,1}, {0,-1}};

    private void meltSnow(World world) {
        org.bukkit.Chunk[] allChunks = world.getLoadedChunks();
        if (allChunks.length == 0) return;
        int meltPerTick = weatherConfig.getMeltChunksPerTick();
        int processed = 0;
        int start = chunkIndex % allChunks.length;
        for (int i = 0; i < allChunks.length && processed < meltPerTick; i++) {
            int idx = (start + i) % allChunks.length;
            processMeltChunk(allChunks[idx]);
            processed++;
        }
        chunkIndex = (chunkIndex + meltPerTick) % allChunks.length;
    }

    private void processMeltChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX() * 16;
        int cz = chunk.getZ() * 16;
        int melted = 0;

        for (int x = 0; x < 16 && melted < 4; x++) {
            for (int z = 0; z < 16 && melted < 4; z++) {
                Block highest = world.getHighestBlockAt(cx + x, cz + z);
                if (highest.getType() != Material.SNOW) continue;

                org.bukkit.block.Biome biome = highest.getBiome();
                double temp = tempCalc.calculate(clock.calculateDayOfYear(), biome);
                if (temp < weatherConfig.getMeltThreshold()) continue;

                Snow snow = (Snow) highest.getBlockData();
                int currentLayers = snow.getLayers();
                int meltSpeed = weatherConfig.getMeltSpeed();

                boolean willDisappear = currentLayers <= meltSpeed;

                if (willDisappear) {
                    highest.setType(Material.AIR, false);
                    totalMelted++;
                    melted++;

                    if (weatherConfig.getSnowMeltBonemeal()) {
                        Block below = highest.getRelative(0, -1, 0);
                        if (below.getType() == Material.GRASS_BLOCK) {
                            below.applyBoneMeal(org.bukkit.block.BlockFace.UP);
                        }
                    }
                } else {
                    snow.setLayers(currentLayers - meltSpeed);
                    highest.setBlockData(snow, false);
                    totalMelted += meltSpeed;
                    melted++;
                }
            }
        }
    }

    private void processChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX() * 16;
        int cz = chunk.getZ() * 16;
        int doy = clock.calculateDayOfYear();

        long startNs = System.nanoTime();

        int layersPerScan = weatherConfig.getLayersPerScan();
        int placed = 0, grown = 0, noGround = 0, notCapable = 0, tooWarm = 0;

        // Phase 1: Grow existing snow on ALL columns – no limit
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                if (tryGrowColumn(cx + bx, cz + bz, world, doy)) {
                    grown++;
                }
            }
        }

        // Phase 2: Place new snow on shuffled columns, limited to layersPerScan
        java.util.List<int[]> cols = new java.util.ArrayList<>(256);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                cols.add(new int[]{cx + bx, cz + bz});
            }
        }
        java.util.Collections.shuffle(cols, random);

        for (int[] col : cols) {
            if (placed >= layersPerScan) break;

            int result = tryPlaceColumn(col[0], col[1], world, doy);
            switch (result) {
                case 1: placed++; break;
                case -1: noGround++; break;
                case -2: notCapable++; break;
                case -3: tooWarm++; break;
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        if (durationMs > 10 || placed + grown > 0) {
            plugin.getLogger().info(String.format(
                "[SnowAcc] chunk %d,%d placed=%d grown=%d noGround=%d notCapable=%d tooWarm=%d %dms",
                chunk.getX(), chunk.getZ(), placed, grown, noGround, notCapable, tooWarm, durationMs));
        }
    }

    /**
     * Try to grow existing snow on this column (Case 1 from processColumn).
     * @return true if growth succeeded
     */
    private boolean tryGrowColumn(int wx, int wz, World world, int dayOfYear) {
        Block top = world.getHighestBlockAt(wx, wz);
        if (top == null) return false;
        if (top.getType() != Material.SNOW) return false;

        double temp = tempCalc.calculate(dayOfYear, top.getBiome());
        if (temp >= weatherConfig.getFreezeThreshold()) return false;

        int maxHeight = getMaxSnowHeight(temp);
        return enoughNeighborsSnowOrBlocked(top) && growColumnSnow(top, maxHeight);
    }

    /**
     * Try to place new snow on this column (Case 2+3 from processColumn).
     * @return 1=placed, -1=noGround, -2=notCapable, -3=tooWarm, 0=skipped
     */
    private int tryPlaceColumn(int wx, int wz, World world, int dayOfYear) {
        Block top = world.getHighestBlockAt(wx, wz);
        if (top == null) return 0;

        Material topMat = top.getType();
        double temp = tempCalc.calculate(dayOfYear, top.getBiome());
        if (temp >= weatherConfig.getFreezeThreshold()) return -3;

        // Skip columns that already have snow – they're handled by tryGrowColumn
        if (topMat == Material.SNOW || topMat == Material.SNOW_BLOCK) return 0;

        // Case 2: Top block is a plant/fungus – remove and find ground below
        if (isReplaceablePlant(topMat)) {
            removePlantAt(top);
            // Fall through to case 3: find solid ground and place
        }

        // Case 3: Top block is something else – check if snow-capable solid ground exists
        Block ground = findColumnGround(top, world);
        if (ground == null) return -1;

        // Only place on full blocks (no fences, walls, glass panes, torches)
        if (!isSnowCapable(ground)) return -2;

        Block above = ground.getRelative(BlockFace.UP);
        Material aboveMat = above.getType();

        // Don't overwrite existing snow
        if (aboveMat == Material.SNOW || aboveMat == Material.SNOW_BLOCK) return 0;

        // Don't place on non-air, non-plant blocks
        if (!above.isEmpty() && !isReplaceablePlant(aboveMat)) return 0;

        if (isReplaceablePlant(aboveMat)) {
            removePlantAt(above);
        }

        Snow snowData = (Snow) Material.SNOW.createBlockData();
        snowData.setLayers(1);
        above.setBlockData(snowData, false);
        totalPlaced++;
        return 1;
    }

    /**
     * Finds the solid ground block for snow placement, starting from the given top block.
     * Returns null if no suitable ground found within configured search depth.
     */
    private Block findColumnGround(Block top, World world) {
        int maxDown = weatherConfig.getMaxDownSearchTicks();
        Block cursor = top;

        for (int i = 0; i < maxDown; i++) {
            Material mat = cursor.getType();
            if (mat == Material.WATER || mat == Material.LAVA) {
                totalSkippedOceans++;
                return null;
            }
            if (mat.isSolid()) {
                return cursor;
            }
            cursor = cursor.getRelative(BlockFace.DOWN);
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
     * Returns true if at least one of the 4 horizontal neighbors already has snow
     * or is blocked (non-snow-capable, unloaded, tall obstacle).
     * If all 4 neighbors are empty snow-capable columns, growth is suppressed
     * so that lateral spread takes priority.
     */
    private boolean enoughNeighborsSnowOrBlocked(Block snow) {
        int minNeighbors = weatherConfig.getMinNeighborsForGrowth();
        World world = snow.getWorld();
        int sx = snow.getX();
        int sy = snow.getY();
        int sz = snow.getZ();
        int blockedOrSnow = 0;

        for (int[] dir : NEIGHBORS) {
            int nx = sx + dir[0];
            int nz = sz + dir[1];

            // Unloaded neighbor → blocked
            if (!world.isChunkLoaded(nx >> 4, nz >> 4)) { blockedOrSnow++; continue; }

            Block neighborTop = world.getHighestBlockAt(nx, nz);
            if (neighborTop == null) { blockedOrSnow++; continue; }

            // Neighbor has snow at same level
            if (neighborTop.getType() == Material.SNOW && neighborTop.getY() == sy) {
                blockedOrSnow++;
                continue;
            }

            // Tall obstacle in the way → blocked
            if (neighborTop.getY() > sy) { blockedOrSnow++; continue; }

            // Neighbor ground is not snow-capable (fence, wall, pane) → blocked
            if (!isSnowCapable(neighborTop) && neighborTop.getY() == sy - 1) {
                blockedOrSnow++;
                continue;
            }

            // Otherwise: neighbor is empty but snow-capable → not blocked
        }
        return blockedOrSnow >= minNeighbors;
    }

    /**
     * Attempt to grow a snow layer thicker at this column.
     */
    private boolean growColumnSnow(Block snowBlock, int maxHeight) {
        Snow snow = (Snow) snowBlock.getBlockData();
        int current = snow.getLayers();
        int limit = Math.min(maxHeight, snow.getMaximumLayers());

        if (current < limit) {
            snow.setLayers(current + 1);
            snowBlock.setBlockData(snow, false);
            totalPlaced++;
            return true;
        } else if (current >= snow.getMaximumLayers()) {
            // Snow block is full – convert to SNOW_BLOCK and start new layer above
            Block above = snowBlock.getRelative(BlockFace.UP);
            if (above.isEmpty()) {
                snowBlock.setType(Material.SNOW_BLOCK, false);
                Snow newLayer = (Snow) Material.SNOW.createBlockData();
                newLayer.setLayers(1);
                above.setBlockData(newLayer, false);
                totalPlaced++;
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the material is a replaceable plant (grass, fern, flower, etc.).
     */
    private boolean isReplaceablePlant(Material mat) {
        return REPLACEABLE_PLANTS.contains(mat) || mat == Material.SHORT_GRASS
            || DOUBLE_PLANTS.contains(mat);
    }

    private static final java.util.Set<Material> REPLACEABLE_PLANTS = java.util.EnumSet.of(
        Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
        Material.DEAD_BUSH, Material.VINE, Material.GLOW_LICHEN,
        Material.PEONY, Material.ROSE_BUSH, Material.LILAC, Material.SUNFLOWER,
        Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID, Material.ALLIUM,
        Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP,
        Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY,
        Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE,
        Material.SWEET_BERRY_BUSH,
        Material.WARPED_ROOTS, Material.CRIMSON_ROOTS,
        Material.WARPED_FUNGUS, Material.CRIMSON_FUNGUS,
        Material.NETHER_SPROUTS, Material.WEEPING_VINES, Material.TWISTING_VINES,
        Material.CAVE_VINES, Material.KELP, Material.SEAGRASS, Material.TALL_SEAGRASS,
        Material.BROWN_MUSHROOM, Material.RED_MUSHROOM
    );

    public int getMaxSnowHeight(double temperature) {
        int base = weatherConfig.getMaxNaturalHeight();
        if (temperature >= weatherConfig.getFreezeThreshold()) return base;
        int extra = (int) ((weatherConfig.getFreezeThreshold() - temperature) / 0.2)
                    * weatherConfig.getHeightPerCold();
        return base + extra;
    }

    private static final java.util.Set<Material> DOUBLE_PLANTS = java.util.EnumSet.of(
        Material.TALL_GRASS,
        Material.LARGE_FERN,
        Material.SUNFLOWER,
        Material.LILAC,
        Material.ROSE_BUSH,
        Material.PEONY
    );

    private void removePlantAt(Block plant) {
        Material type = plant.getType();
        if (DOUBLE_PLANTS.contains(type)) {
            // Remove both halves: the one above is also the same Material
            Block above = plant.getRelative(0, 1, 0);
            if (above.getType() == type) {
                above.setType(Material.AIR, false);
            }
        }
        plant.setType(Material.AIR, false);
    }
}