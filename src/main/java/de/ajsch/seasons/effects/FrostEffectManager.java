package de.ajsch.seasons.effects;

import de.ajsch.seasons.config.FrostConfig;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages frost-based particle effects for players in cold temperatures.
 * Periodically checks each online player's temperature and spawns
 * {@link Particle#SNOWFLAKE} particles when frost conditions are met.
 *
 * <p>Frost factor scales linearly between {@code freezeThreshold} (0 intensity)
 * and {@code fullFrostThreshold} (1.0 intensity). Particles are only spawned
 * in biomes that are not in the excluded-biomes list from {@code frost.yml}.
 */
public class FrostEffectManager {

    private final JavaPlugin plugin;
    private final FrostConfig config;
    private final TemperatureCalculator temperatureCalculator;
    private final SeasonClock seasonClock;
    private BukkitTask task;

    private static final long TICK_INTERVAL = 80L; // 4 seconds

    public FrostEffectManager(JavaPlugin plugin,
                              FrostConfig config,
                              TemperatureCalculator temperatureCalculator,
                              SeasonClock seasonClock) {
        this.plugin = plugin;
        this.config = config;
        this.temperatureCalculator = temperatureCalculator;
        this.seasonClock = seasonClock;
    }

    /**
     * Starts the periodic particle spawn task.
     * Runs every {@value #TICK_INTERVAL} ticks (4 seconds).
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tick,
                20L,   // initial delay of 1 second, so configs are ready
                TICK_INTERVAL
        );
    }

    /**
     * Stops the scheduled task and cleans up.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Called periodically. Iterates over all online players and spawns
     * snowflake particles when frost conditions are met.
     */
    void tick() {
        if (!config.isParticlesEnabled()) {
            return;
        }

        int dayOfYear = seasonClock.calculateDayOfYear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            // Skip excluded biomes
            if (!config.isFrostAllowedInBiome(player.getLocation().getBlock().getBiome())) {
                continue;
            }

            double temperature = temperatureCalculator.calculateWithDayTime(
                    dayOfYear,
                    player.getLocation().getBlock().getBiome(),
                    player.getWorld().getFullTime()
            );

            double frostFactor = getFrostFactor(temperature);
            if (frostFactor <= 0.0) {
                continue;
            }

            spawnFrostParticles(player, frostFactor);
        }
    }

    /**
     * Calculates the frost factor (0.0 to 1.0) based on the current temperature.
     * Linear interpolation between {@code freezeThreshold} (0) and
     * {@code fullFrostThreshold} (1.0).
     *
     * @param temperature current temperature at the player's location
     * @return frost factor between 0.0 and 1.0, or 0.0 if above freeze threshold
     */
    public double getFrostFactor(double temperature) {
        if (temperature >= config.getFreezeThreshold()) {
            return 0.0;
        }
        double range = config.getFreezeThreshold() - config.getFullFrostThreshold();
        double progress = (config.getFreezeThreshold() - temperature) / range;
        return Math.clamp(progress, 0.0, 1.0);
    }

    /**
     * Spawns {@code SNOWFLAKE} particles around a player.
     *
     * @param player      the target player
     * @param frostFactor intensity multiplier (0.0 to 1.0)
     */
    private void spawnFrostParticles(Player player, double frostFactor) {
        int countPerSecond = config.getParticlesPerSecond();
        // The task runs every 4 seconds, so scale appropriately:
        int countPerInterval = Math.max(1, (int) (countPerSecond * frostFactor * 4.0));
        double spread = config.getSpreadRadius();

        player.getWorld().spawnParticle(
                Particle.SNOWFLAKE,
                player.getLocation(),
                countPerInterval,
                spread,   // spreadX
                2.0,      // spreadY
                spread,   // spreadZ
                0          // extra: no speed (floating particles)
        );
    }

    /**
     * Checks whether the task is currently running.
     *
     * @return true if the repeating task is active
     */
    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }
}