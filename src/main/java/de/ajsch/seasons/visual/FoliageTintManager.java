package de.ajsch.seasons.visual;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.visual.nms.NmsAdapter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates per-player biome foliage tints and dispatches them via {@link NmsAdapter}.
 * Only processes currently loaded chunks – never forces chunk loads.
 */
public class FoliageTintManager implements Listener {

    private final JavaPlugin plugin;
    private final NmsAdapter nmsAdapter;
    private final VisualConfig visualConfig;
    private final int viewDistance;
    private final int biomeSampleStep;

    /** Cache: chunk key → set of biomes present in that chunk. */
    private final Map<Long, Set<Biome>> chunkBiomeCache = new ConcurrentHashMap<>();

    public FoliageTintManager(JavaPlugin plugin, NmsAdapter nmsAdapter, VisualConfig visualConfig) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.visualConfig = visualConfig;
        this.viewDistance = plugin.getServer().getViewDistance();
        // Sample every N blocks within a chunk to discover biomes without scanning all 256 positions
        this.biomeSampleStep = 4;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Update foliage tints for a specific player.
     *
     * @param player             the target player
     * @param currentSeason      the season we are transitioning from
     * @param targetSeason       the season we are transitioning to
     * @param transitionProgress 0.0 = full currentSeason, 1.0 = full targetSeason
     */
    public void updatePlayerTints(Player player, Season currentSeason, Season targetSeason,
                                  double transitionProgress) {
        plugin.getLogger().info("[FoliageTintManager] updatePlayerTints called for " + player.getName()
            + " from " + currentSeason + " to " + targetSeason + " progress=" + String.format("%.2f", transitionProgress));
        World world = player.getWorld();
        Chunk centerChunk = player.getLocation().getChunk();
        int cx = centerChunk.getX();
        int cz = centerChunk.getZ();

        Set<Biome> seenBiomes = new HashSet<>();

        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int chunkX = cx + dx;
                int chunkZ = cz + dz;
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;

                long chunkKey = chunkKey(chunkX, chunkZ);
                Set<Biome> cached = chunkBiomeCache.get(chunkKey);
                if (cached != null) {
                    seenBiomes.addAll(cached);
                } else {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    Set<Biome> biomes = collectBiomesFromChunk(chunk);
                    chunkBiomeCache.put(chunkKey, biomes);
                    seenBiomes.addAll(biomes);
                }
            }
        }

        for (Biome biome : seenBiomes) {
            int color = ColorCalculator.calculateSeasonalColor(
                currentSeason, targetSeason, transitionProgress, biome, visualConfig);
            String biomeKey = biome.getKey().toString();
            nmsAdapter.sendBiomeTint(player, biomeKey, color, color);
        }

        // Flush all pending tints in ONE registry-patch + ONE chunk refresh
        nmsAdapter.flushTints(player);
    }

    /**
     * Reset a player back to vanilla foliage colors (summer defaults, no transition).
     */
    public void resetPlayerToVanilla(Player player) {
        updatePlayerTints(player, Season.SUMMER, Season.SUMMER, 0.0);
    }

    /**
     * Bulk-update all online players with the given seasonal tint.
     */
    public void updateAllOnlinePlayers(Season currentSeason, Season targetSeason,
                                       double transitionProgress) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerTints(player, currentSeason, targetSeason, transitionProgress);
        }
    }

    /**
     * Sample a loaded chunk at a sparse grid of block positions to collect all distinct biomes.
     *
     * @return the set of distinct biomes found in this chunk
     */
    private Set<Biome> collectBiomesFromChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        Set<Biome> seenBiomes = new HashSet<>();

        for (int dx = 0; dx < 16; dx += biomeSampleStep) {
            for (int dz = 0; dz < 16; dz += biomeSampleStep) {
                int blockX = baseX + dx;
                int blockZ = baseZ + dz;
                int y = world.getHighestBlockYAt(blockX, blockZ);
                Biome biome = world.getBiome(blockX, y, blockZ);
                seenBiomes.add(biome);
            }
        }
        return seenBiomes;
    }

    /**
     * Build a compact key from chunk X/Z coordinates.
     */
    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Invalidate the biome cache for a chunk when it unloads.
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        chunkBiomeCache.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }
}