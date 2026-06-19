package de.ajsch.seasons.weather;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class WeatherInterceptor implements Listener {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final BiomeTemperature biomeTemp;
    private final WeatherConfig weatherConfig;
    private BukkitRunnable snowParticleTask;

    public WeatherInterceptor(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                              BiomeTemperature biomeTemp, WeatherConfig weatherConfig) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.biomeTemp = biomeTemp;
        this.weatherConfig = weatherConfig;
    }

    public void startSnowParticleScheduler() {
        if (snowParticleTask != null && !snowParticleTask.isCancelled()) {
            snowParticleTask.cancel();
        }
        plugin.getLogger().info("SnowParticleScheduler started (radius=" + weatherConfig.getParticleRadius() + ", count=" + weatherConfig.getParticleCount() + ", y=" + weatherConfig.getParticleYMin() + "-" + weatherConfig.getParticleYMax() + ")");
        snowParticleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!weatherConfig.isEnabled()) return;
                if (clock.getCurrentSeason() != Season.WINTER) return;

                for (World world : plugin.getServer().getWorlds()) {
                    if (!world.hasStorm()) continue;

                    for (Player player : world.getPlayers()) {
                        org.bukkit.block.Biome biome = player.getLocation().getBlock().getBiome();
                        PrecipitationCategory category = biomeTemp.getCategory(biome);
                        double temperature = tempCalc.calculate(clock.calculateDayOfYear(), biome);

                        if (category == PrecipitationCategory.CAN_FREEZE
                            && temperature < weatherConfig.getFreezeThreshold()) {
                            spawnSnowParticles(player);
                        }
                    }
                }
            }
        };
        snowParticleTask.runTaskTimer(plugin, 0L, 3L);
    }

    public void stopSnowParticleScheduler() {
        if (snowParticleTask != null) {
            snowParticleTask.cancel();
            snowParticleTask = null;
        }
    }

    public void resetAllPlayerWeather() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                player.resetPlayerWeather();
            }
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!weatherConfig.isEnabled()) return;
        if (!event.toWeatherState()) return;

        World world = event.getWorld();
        Season currentSeason = clock.getCurrentSeason();

        for (Player player : world.getPlayers()) {
            org.bukkit.block.Biome biome = player.getLocation().getBlock().getBiome();
            PrecipitationCategory category = biomeTemp.getCategory(biome);
            double temperature = tempCalc.calculate(clock.calculateDayOfYear(), biome);

            if (currentSeason == Season.WINTER
                && category == PrecipitationCategory.CAN_FREEZE
                && temperature < weatherConfig.getFreezeThreshold()) {
                player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
            }
        }
    }

    private final java.util.Random particleRandom = new java.util.Random();

    private void spawnSnowParticles(Player player) {
        int radius = weatherConfig.getParticleRadius();
        int count = weatherConfig.getParticleCount();
        org.bukkit.Location center = player.getLocation();
        for (int i = 0; i < count; i++) {
        double dx = (particleRandom.nextDouble() - 0.5) * radius * 2;
        double dz = (particleRandom.nextDouble() - 0.5) * radius * 2;
        double dy = weatherConfig.getParticleYMin() + particleRandom.nextDouble() * (weatherConfig.getParticleYMax() - weatherConfig.getParticleYMin());
        player.getWorld().spawnParticle(
            Particle.SNOWFLAKE,
            center.getX() + dx, center.getY() + dy, center.getZ() + dz,
            1, 0, -0.05, 0, 0.02
        );
        }
    }
}