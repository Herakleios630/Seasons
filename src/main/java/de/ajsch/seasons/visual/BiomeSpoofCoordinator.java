package de.ajsch.seasons.visual;

import de.ajsch.seasons.SeasonsPlugin;
import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Zentrale Koordinator-Klasse für das Biome-Spoofing.
 * Verantwortlich für Timer, Spieler-Loop, Budget-Management und
 * die run()-Methode. Nutzt {@link SeasonBiomeResolver} für die
 * Klassifizierung und Target-Auswahl sowie {@link ChunkBiomeApplier}
 * für die tatsächliche Biome-Überschreibung.
 *
 * <p>Ersetzt die alte {@code BiomeSpoofAdapter}-Monolith-Klasse.</p>
 */
public class BiomeSpoofCoordinator implements Runnable {

    private final SeasonsPlugin plugin;
    private final SeasonClock clock;
    private final ConfigManager config;
    private final TemperatureCalculator tempCalc;
    private final BiomeBackupStore backupStore;
    private final SeasonBiomeResolver resolver;
    private final ChunkBiomeApplier applier;
    private final TransitionManager transitionManager;
    private final Logger logger;

    private SpoofMode mode = SpoofMode.OFF;
    private int taskId = -1;
    private int radiusChunks;
    private int budgetPerTick;
    private int transitionDays;
    private boolean revertOnNonWinter;

    private final Set<String> disabledWorlds = new HashSet<>();

    private List<int[]> cachedOffsets = new ArrayList<>();

    // Transition-Fenster (ms seit Epoche), 0 = inaktiv
    private long seasonTransitionUntil = 0;

    // ---------------------------------------------------------------
    //  Konstruktor
    // ---------------------------------------------------------------

    public BiomeSpoofCoordinator(SeasonsPlugin plugin, SeasonClock clock,
                                 ConfigManager config, TemperatureCalculator tempCalc,
                                 BiomeBackupStore backupStore,
                                 SeasonBiomeResolver resolver, ChunkBiomeApplier applier,
                                 TransitionManager transitionManager) {
        this.plugin = plugin;
        this.clock = clock;
        this.config = config;
        this.tempCalc = tempCalc;
        this.backupStore = backupStore;
        this.resolver = resolver;
        this.applier = applier;
        this.transitionManager = transitionManager;
        this.logger = plugin.getLogger();
    }

    public ChunkBiomeApplier getApplier() {
        return applier;
    }

    // ---------------------------------------------------------------
    //  Config-Reload
    // ---------------------------------------------------------------

    /**
     * Liest sämtliche Spoofing-Werte aus dem ConfigManager und
     * befüllt die internen Maps. Delegiert Klassifizierungs-Werte
     * an den SeasonBiomeResolver.
     */
    public void reloadFromConfig() {
        mode = SpoofMode.fromString(config.getSpoofMode());
        if (!config.isBiomeSpoofEnabled()) {
            mode = SpoofMode.OFF;
        }

        radiusChunks = config.getSpoofRadiusChunks();
        budgetPerTick = config.getSpoofBudgetPerTick();
        transitionDays = config.getSpoofTransitionDays();
        revertOnNonWinter = config.isRevertOnNonWinter();

        disabledWorlds.clear();
        disabledWorlds.addAll(config.getDisabledFxWorlds());

        // Resolver ebenfalls neu laden
        resolver.reloadFromConfig(config);

        // Tracking-Daten leeren (Applier-Maps werden geleert)
        applier.getSpoofedSet().clear();
        applier.getLastAppliedMap().clear();

        // Offsets für den neuen Radius vorberechnen
        cachedOffsets = generateOffsetsByDistance(radiusChunks);

        logger.info(String.format(
            "[BiomeSpoofCoordinator] Config geladen: mode=%s radius=%d budget=%d",
            mode, radiusChunks, budgetPerTick));
    }

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    /**
     * Startet den 40-Tick-Timer, sofern der Spoof-Mode nicht OFF ist.
     */
    public void register() {
        reloadFromConfig();
        if (mode == SpoofMode.OFF) {
            logger.info("[BiomeSpoofCoordinator] Spoofing ist deaktiviert (mode=OFF).");
            return;
        }
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, this, 40L, 40L);
        taskId = task.getTaskId();
        logger.info("[BiomeSpoofCoordinator] 40-Tick-Timer gestartet (taskId=" + taskId + ").");
    }

    /**
     * Stoppt den Timer und setzt alle aktiven Spoofs zurück.
     */
    public void unregister() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        applier.revertAll();
        resolver.clearCaches();
        logger.info("[BiomeSpoofCoordinator] Timer gestoppt.");
    }

    public boolean isRunning() { return taskId != -1; }

    // ---------------------------------------------------------------
    //  run() – Haupt-Loop
    // ---------------------------------------------------------------

    @Override
    public void run() {
        try {
            runInternal();
        } catch (Exception e) {
            logger.severe("[BiomeSpoofCoordinator] Exception in run(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runInternal() {
        if (mode == SpoofMode.OFF) return;

        Season season = clock.getCurrentSeason();

        // TransitionManager ticken (wenn aktiv)
        if (transitionManager.isActive()) {
            long currentTick = Bukkit.getCurrentTick();
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    transitionManager.tick(currentTick, world);
                    break;
                }
            }
        }

        // Aktuelle Variant ermitteln (null wenn keine Transition läuft)
        String currentVariant = transitionManager.getCurrentVariant();

        int budgetRemaining = budgetPerTick;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (budgetRemaining <= 0) break;

            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            if (disabledWorlds.contains(world.getName())) continue;

            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;

            for (int[] offset : cachedOffsets) {
                if (budgetRemaining <= 0) break;

                int cx = playerChunkX + offset[0];
                int cz = playerChunkZ + offset[1];

                if (!world.isChunkLoaded(cx, cz)) continue;

                Chunk chunk = world.getChunkAt(cx, cz);
                String chunkKey = chunkKey(cx, cz);

                if (resolver.isChunkExcludedByConfig(chunk)) continue;

                // Bei aktiver Transition verwenden wir den aktuellen Variant-Namen
                // für das Per-Biome-Mapping (z.B. "early_fall"), sonst den Season-Namen.
                String variant = (currentVariant != null) ? currentVariant : season.name().toLowerCase();
                double temperature = tempCalc.calculate(
                        clock.calculateDayOfYear(),
                        resolver.getSampleBiome(chunk));
                Biome resolved = resolver.resolveTargetBiome(chunk, season, variant, temperature);
                if (resolved == null) {
                    // Fallback: alte Per-Family-Maps
                    BiomeFamily family = resolver.classifyOriginalFamily(chunk, chunkKey);
                    Biome targetBiome = resolver.chooseTargetBiomeForChunk(chunkKey, family, season, chunk);
                    if (targetBiome == null) continue;
                    resolved = targetBiome;
                }

                if (resolver.shouldSkipSpoofForChunk(chunk, season, chunkKey)) continue;

                // Wende Spoof nur an, wenn sich das Ziel-Biom geändert hat
                Map<String, Biome> lastApplied = applier.getLastAppliedMap();
                Biome last = lastApplied.get(chunkKey);
                if (last == null || !resolved.equals(last)) {
                    applier.captureAndApply(chunk, resolved, chunkKey);
                }

                budgetRemaining--;
            }
        }

        // Heartbeat every 200 ticks (10s) for liveness check
        long nowTicks = Bukkit.getCurrentTick();
        if (nowTicks % 200 == 0) {
            int spoofedCount = applier.getSpoofedSet().size();
            logger.info(String.format(
                "[BiomeSpoofCoordinator] Heartbeat: spoofed=%d season=%s online=%d",
                spoofedCount, season.name(), Bukkit.getOnlinePlayers().size()));
        }
    }

    // ---------------------------------------------------------------
    //  Chunk-Utility
    // ---------------------------------------------------------------

    public static String chunkKey(int cx, int cz) {
        return cx + "_" + cz;
    }

    /**
     * Erzeugt alle (dx, dz)-Offsets innerhalb des Quadrats mit
     * Kantenlänge 2*radius+1, sortiert nach euklidischer Distanz.
     */
    public static List<int[]> generateOffsetsByDistance(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                offsets.add(new int[]{dx, dz});
            }
        }
        offsets.sort(Comparator.comparingDouble(o ->
            Math.sqrt((double) o[0] * o[0] + (double) o[1] * o[1])));
        return offsets;
    }

    // ---------------------------------------------------------------
    //  revertChunk / revertAll
    // ---------------------------------------------------------------

    /**
     * Stellt die Original-Biome eines Chunks wieder her.
     * Delegiert an ChunkBiomeApplier.
     */
    public void revertChunk(Chunk chunk) {
        applier.revertChunk(chunk);
        resolver.evictChunk(chunkKey(chunk.getX(), chunk.getZ()));
    }

    /**
     * Setzt alle aktiven Spoofs zurück.
     * Wird beim unregister() und bei Season-Wechseln aufgerufen.
     */
    public void revertAll() {
        applier.revertAll();
        resolver.clearCaches();
    }

    // ---------------------------------------------------------------
    //  Getter (für Debug / Tests / Listener)
    // ---------------------------------------------------------------

    public SpoofMode getMode() { return mode; }
    public int getRadiusChunks() { return radiusChunks; }
    public int getBudgetPerTick() { return budgetPerTick; }
    public boolean isRevertOnNonWinter() { return revertOnNonWinter; }

    /** Anzahl aktuell gespoofter Chunks (aus dem Applier). */
    public int getSpoofedCount() { return applier.getSpoofedSet().size(); }
    /** Anzahl lastApplied-Einträge (aus dem Applier). */
    public int getLastAppliedCount() { return applier.getLastAppliedMap().size(); }

    /** Das spoofed-Set des Appliers (für Listener). */
    public Set<String> getSpoofedSet() { return applier.getSpoofedSet(); }
    /** Die lastApplied-Map des Appliers (für Listener). */
    public Map<String, Biome> getLastAppliedMap() { return applier.getLastAppliedMap(); }

    public long getSeasonTransitionUntil() { return seasonTransitionUntil; }
    public void setSeasonTransitionUntil(long value) { this.seasonTransitionUntil = value; }

    public SeasonBiomeResolver getResolver() { return resolver; }
    public TransitionManager getTransitionManager() { return transitionManager; }

    /**
     * Wird vom {@link BiomeSpoofListener} bei Season-Wechseln aufgerufen.
     * Startet eine neue Transition und revertiert ggf. alle Spoofs.
     */
    public void onSeasonChange(Season from, Season to) {
        transitionManager.cancel();
        if (isRevertOnNonWinter()) {
            revertAll();
        }
        World overworld = null;
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                overworld = w;
                break;
            }
        }
        if (overworld != null) {
            transitionManager.startTransition(from, to, overworld);
        }
        logger.info("[BiomeSpoofCoordinator] Season changed to " + to.getDisplayName());
    }
}