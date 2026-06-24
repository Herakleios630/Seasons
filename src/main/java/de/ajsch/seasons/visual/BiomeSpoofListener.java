package de.ajsch.seasons.visual;

import de.ajsch.seasons.season.SeasonChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.logging.Logger;

/**
 * Listener für Chunk-Load/Unload und Season-Change-Events,
 * der das Biome-Spoofing sauber nachführt.
 *
 * - Bei ChunkLoad: Entfernt alle Tracking-Daten (frisch geladene
 *   Chunks haben Vanilla-Biome, kein Revert nötig).
 * - Bei ChunkUnload: Revertiert gespoofte Chunks, damit keine
 *   inkonsistenten Daten im Speicher bleiben.
 * - Bei SeasonChange: Revertiert alle aktiven Spoofs und setzt
 *   ein Transition-Fenster für den Adapter.
 */
public class BiomeSpoofListener implements Listener {

    private final BiomeSpoofCoordinator coordinator;
    private final BiomeBackupStore backupStore;
    private final Logger logger;

    public BiomeSpoofListener(BiomeSpoofCoordinator coordinator, BiomeBackupStore backupStore, Logger logger) {
        this.coordinator = coordinator;
        this.backupStore = backupStore;
        this.logger = logger;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
        String chunkKey = BiomeSpoofCoordinator.chunkKey(event.getChunk().getX(), event.getChunk().getZ());

        // Chunk wurde frisch geladen → Vanilla-Biome, kein Revert nötig
        coordinator.getSpoofedSet().remove(chunkKey);
        coordinator.getLastAppliedMap().remove(chunkKey);
        if (coordinator.getResolver() != null) {
            coordinator.getResolver().getFamilyCacheMap().remove(chunkKey);
            coordinator.getResolver().getColdChunksSet().remove(chunkKey);
        }
        backupStore.removeBackup(chunkKey);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (event.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
        String chunkKey = BiomeSpoofCoordinator.chunkKey(event.getChunk().getX(), event.getChunk().getZ());

        if (coordinator.getSpoofedSet().contains(chunkKey)) {
            coordinator.revertChunk(event.getChunk());
            logger.fine("[BiomeSpoofListener] Chunk " + chunkKey + " unloaded – reverted spoof.");
        }
    }

    @EventHandler
    public void onSeasonChange(SeasonChangeEvent event) {
        coordinator.onSeasonChange(event.getOldSeason(), event.getNewSeason());
    }
}