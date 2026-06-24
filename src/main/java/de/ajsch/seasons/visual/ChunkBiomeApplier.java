package de.ajsch.seasons.visual;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Führt die tatsächliche Biome-Überschreibung (captureAndApply) und
 * Wiederherstellung (revertChunk, revertAll) auf Chunk-Ebene durch.
 *
 * <p>Extrahiert aus der alten {@code BiomeSpoofAdapter}-Klasse.
 * Verwaltet eigene {@link #spoofed}- und {@link #lastApplied}-Maps.</p>
 */
public class ChunkBiomeApplier {

    private final BiomeBackupStore backupStore;
    private final Logger logger;

    // Spoof-Tracking: welche Chunks sind aktuell gespooft?
    private final Set<String> spoofed = new HashSet<>();
    // Letztes angewandtes Ziel-Biom pro Chunk (für Change-Detection)
    private final Map<String, Biome> lastApplied = new HashMap<>();

    public ChunkBiomeApplier(BiomeBackupStore backupStore, Logger logger) {
        this.backupStore = backupStore;
        this.logger = logger;
    }

    // ---------------------------------------------------------------
    //  captureAndApply
    // ---------------------------------------------------------------

    /**
     * Sichert die Original-Biome eines Chunks (first-touch),
     * überschreibt alle Biome-Positionen mit dem targetBiome und
     * refreshed den Chunk client-seitig.
     */
    public void captureAndApply(Chunk chunk, Biome targetBiome, String chunkKey) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int sections = (maxY - minY) / 4;
        int expected = 4 * 4 * sections; // 16/4=4 steps x, 16/4=4 steps z, alle sections
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        // 4×4 Biome-Sample-Raster pro Sektion ueber 16×16 Bloecke
        Biome[] originalBiomes = new Biome[expected];
        int index = 0;
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    originalBiomes[index++] = world.getBiome(baseX + x, y, baseZ + z);
                }
            }
        }

        // Backup nur beim ersten Touch (saveFirstTouch prüft das intern)
        backupStore.saveFirstTouch(chunk, originalBiomes);

        // Alle Biome-Positionen im Chunk überschreiben
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    world.setBiome(baseX + x, y, baseZ + z, targetBiome);
                }
            }
        }

        world.refreshChunk(chunk.getX(), chunk.getZ());

        // Re-send chunk to force client to re-request biome data
        world.unloadChunk(chunkX, chunkZ);
        world.getChunkAt(chunkX, chunkZ);

        boolean isFirstTouch = !spoofed.contains(chunkKey);
        spoofed.add(chunkKey);
        lastApplied.put(chunkKey, targetBiome);

        if (isFirstTouch || spoofed.size() % 50 == 0) {
            logger.info(String.format(
                "[ChunkBiomeApplier] captureAndApply chunk=%s -> %s (first=%s, totalSpoofed=%d)",
                chunkKey, targetBiome.name(), isFirstTouch, spoofed.size()));
        }
    }

    // ---------------------------------------------------------------
    //  revertChunk
    // ---------------------------------------------------------------

    /**
     * Stellt die Original-Biome eines Chunks wieder her.
     */
    public void revertChunk(Chunk chunk) {
        String chunkKey = BiomeSpoofCoordinator.chunkKey(chunk.getX(), chunk.getZ());

        if (!backupStore.hasBackup(chunkKey)) {
            spoofed.remove(chunkKey);
            lastApplied.remove(chunkKey);
            return;
        }

        Biome[] originalBiomes = backupStore.getBackup(chunkKey);
        if (originalBiomes == null || originalBiomes.length == 0) {
            backupStore.removeBackup(chunkKey);
            spoofed.remove(chunkKey);
            lastApplied.remove(chunkKey);
            return;
        }

        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int sections = (maxY - minY) / 4;
        int expected = 4 * 4 * sections; // 16/4=4 steps x, 16/4=4 steps z, alle sections

        if (originalBiomes.length != expected) {
            logger.warning(String.format(
                "[ChunkBiomeApplier] Backup-Größe (%d) passt nicht zu erwartet (%d) für Chunk %s – überspringe Revert.",
                originalBiomes.length, expected, chunkKey));
            backupStore.removeBackup(chunkKey);
            spoofed.remove(chunkKey);
            lastApplied.remove(chunkKey);
            return;
        }

        int index = 0;
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    Biome original = originalBiomes[index++];
                    if (original != null) {
                        world.setBiome(
                            (chunk.getX() << 4) + x,
                            y,
                            (chunk.getZ() << 4) + z,
                            original
                        );
                    }
                }
            }
        }

        world.refreshChunk(chunk.getX(), chunk.getZ());

        spoofed.remove(chunkKey);
        lastApplied.remove(chunkKey);
        backupStore.removeBackup(chunkKey);
    }

    // ---------------------------------------------------------------
    //  revertAll
    // ---------------------------------------------------------------

    /**
     * Setzt alle aktiven Spoofs zurück (wird beim unregister()
     * und bei Season-Wechseln aufgerufen).
     */
    public void revertAll() {
        for (String chunkKey : new ArrayList<>(spoofed)) {
            String[] parts = chunkKey.split("_");
            if (parts.length != 2) {
                spoofed.remove(chunkKey);
                lastApplied.remove(chunkKey);
                continue;
            }
            try {
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                // Revert nur wenn der Chunk noch geladen ist
                for (World world : Bukkit.getWorlds()) {
                    if (world.isChunkLoaded(cx, cz)) {
                        Chunk chunk = world.getChunkAt(cx, cz);
                        revertChunk(chunk);
                        break;
                    }
                }
                // Chunk nicht geladen → nur aus Maps entfernen
                if (spoofed.contains(chunkKey)) {
                    spoofed.remove(chunkKey);
                    lastApplied.remove(chunkKey);
                    backupStore.removeBackup(chunkKey);
                }
            } catch (NumberFormatException e) {
                spoofed.remove(chunkKey);
                lastApplied.remove(chunkKey);
            }
        }
        logger.fine("[ChunkBiomeApplier] revertAll() – " + spoofed.size()
            + " verbleibend.");
    }

    // ---------------------------------------------------------------
    //  Getter
    // ---------------------------------------------------------------

    public Set<String> getSpoofedSet() { return spoofed; }
    public Map<String, Biome> getLastAppliedMap() { return lastApplied; }
}