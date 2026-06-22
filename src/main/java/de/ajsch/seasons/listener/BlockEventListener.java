package de.ajsch.seasons.listener;

import de.ajsch.seasons.persistence.ChunkCacheStore;
import de.ajsch.seasons.weather.ChunkCacheManager;
import de.ajsch.seasons.weather.SnowAccumulator;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Invalidates the chunk snow cache on block changes and chunk unloads
 * so that stale cache data is never used for snow placement decisions.
 * Also marks the cache entry as dirty for persistence.
 */
public class BlockEventListener implements Listener {

    private final SnowAccumulator accumulator;
    private final ChunkCacheManager cacheManager;

    public BlockEventListener(SnowAccumulator accumulator, ChunkCacheManager cacheManager) {
        this.accumulator = accumulator;
        this.cacheManager = cacheManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        invalidate(event.getBlock().getChunk());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        invalidate(event.getBlock().getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        invalidate(event.getChunk());
    }

    private void invalidate(Chunk chunk) {
        String key = ChunkCacheManager.buildCacheKey(chunk);
        accumulator.invalidateChunk(chunk);
        cacheManager.markDirty(key);
    }
}