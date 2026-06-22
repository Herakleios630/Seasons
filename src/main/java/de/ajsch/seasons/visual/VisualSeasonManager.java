package de.ajsch.seasons.visual;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates foliage tint updates for all online players.
 * Listens to join/quit/season-change events and runs a periodic update task.
 */
public class VisualSeasonManager implements Listener {

    private final JavaPlugin plugin;
    private final FoliageTintManager tintManager;
    private final VisualConfig visualConfig;

    /** How often (in ticks) to run the periodic tint update during season transitions. */
    private final long updateIntervalTicks;

    /** Per-player state for tracking active tints. */
    private final Map<UUID, PlayerVisualState> playerStates = new ConcurrentHashMap<>();

    private Season currentSeason;
    private double transitionProgress;
    private boolean inTransition;
    private int transitionTick;

    private BukkitTask periodicTask;

    public VisualSeasonManager(JavaPlugin plugin, FoliageTintManager tintManager,
                               VisualConfig visualConfig) {
        this.plugin = plugin;
        this.tintManager = tintManager;
        this.visualConfig = visualConfig;
        // Default: update every 200 ticks (10 seconds) – configurable via VisualConfig
        this.updateIntervalTicks = visualConfig.getUpdateIntervalTicks();
        this.currentSeason = Season.SUMMER;
        this.transitionProgress = 0.0;
        this.inTransition = false;
        this.transitionTick = 0;
    }

    /**
     * Start the periodic update task and register event listeners.
     * Must be called after construction.
     */
    public void start() {
        periodicTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::onTick, 20L, updateIntervalTicks);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[VisualSeasonManager] Started with update interval "
            + updateIntervalTicks + " ticks");
    }

    /**
     * Stop the periodic task. Called on plugin disable.
     */
    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        playerStates.clear();
    }

    // ----------------------------------------------------------------
    //  Event handlers
    // ----------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerStates.put(player.getUniqueId(), new PlayerVisualState());
        tintManager.updatePlayerTints(player, currentSeason, currentSeason,
            inTransition ? transitionProgress : 0.0);
        plugin.getLogger().fine("[Visual] Sent initial tints to joining player " + player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSeasonChange(SeasonChangeEvent event) {
        Season oldSeason = event.getOldSeason();
        Season newSeason = event.getNewSeason();
        plugin.getLogger().info(String.format(
            "[Visual] Season changed: %s → %s. Starting transition.",
            oldSeason.getDisplayName(), newSeason.getDisplayName()));

        currentSeason = newSeason;
        transitionProgress = 1.0;  // DEBUG: immediate switch, no transition
        transitionTick = 0;
        inTransition = false;      // DEBUG: skip transition ticks

        // Immediately push the final target season colors
        tintManager.updateAllOnlinePlayers(oldSeason, newSeason, 1.0);
    }

    // ----------------------------------------------------------------
    //  Periodic tick
    // ----------------------------------------------------------------

    private void onTick() {
        if (!inTransition) {
            // No transition active – nothing to do
            return;
        }

        transitionTick++;
        double transitionDays = visualConfig.getTransitionDays();
        long transitionTicksTotal = (long) (transitionDays * 24000L);
        long elapsedTicks = transitionTick * updateIntervalTicks;

        if (elapsedTicks >= transitionTicksTotal) {
            // Transition complete
            transitionProgress = 1.0;
            inTransition = false;
            plugin.getLogger().info("[Visual] Transition to " + currentSeason.getDisplayName()
                + " complete.");
        } else {
            transitionProgress = (double) elapsedTicks / transitionTicksTotal;
        }

        tintManager.updateAllOnlinePlayers(
            previousSeasonFor(currentSeason),
            currentSeason,
            transitionProgress
        );
    }

    /**
     * Returns the season that precedes the given season.
     * Used to determine the "from" season during a transition.
     */
    private static Season previousSeasonFor(Season season) {
        return switch (season) {
            case SPRING -> Season.WINTER;
            case SUMMER -> Season.SPRING;
            case FALL   -> Season.SUMMER;
            case WINTER -> Season.FALL;
        };
    }

    // ----------------------------------------------------------------
    //  PlayerVisualState inner class
    // ----------------------------------------------------------------

    /**
     * Holds per-player visual state.
     * Fields can be extended in later phases (e.g., custom biome palettes).
     */
    private static class PlayerVisualState {
        private int lastUpdateTick;
        private boolean isActive;

        PlayerVisualState() {
            this.lastUpdateTick = 0;
            this.isActive = true;
        }
    }
}