package de.ajsch.seasons.listener;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonChangeEvent;
import de.ajsch.seasons.weather.WeatherConfig;
import de.ajsch.seasons.weather.WeatherInterceptor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class SeasonChangeListener implements Listener {

    private final JavaPlugin plugin;
    private final WeatherInterceptor weatherInterceptor;
    private final WeatherConfig weatherConfig;

    public SeasonChangeListener(JavaPlugin plugin, WeatherInterceptor weatherInterceptor,

                                WeatherConfig weatherConfig) {
        this.plugin = plugin;
        this.weatherInterceptor = weatherInterceptor;
        this.weatherConfig = weatherConfig;
    }

    @EventHandler
    public void onSeasonChange(SeasonChangeEvent event) {
        weatherInterceptor.resetAllPlayerWeather();

        if (event.getNewSeason() == Season.WINTER) {
            weatherInterceptor.startSnowParticleScheduler();
        } else {
            weatherInterceptor.stopSnowParticleScheduler();
        }

        // Frühjahrs-Regeneration: BoneMeal auf freie Grasblöcke
        if (event.getNewSeason() == Season.SPRING && weatherConfig.getSpringRegenerationBonemeal()) {
            triggerSpringRegeneration();
        }
    }

    private void triggerSpringRegeneration() {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            int count = 0;
            for (Chunk chunk : world.getLoadedChunks()) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block highest = world.getHighestBlockAt(
                            chunk.getX() * 16 + x,
                            chunk.getZ() * 16 + z
                        );
                        if (highest.getType() == Material.GRASS_BLOCK) {
                            Block above = highest.getRelative(BlockFace.UP);
                            if (above.isEmpty()) {
                                highest.applyBoneMeal(BlockFace.UP);
                                count++;
                            }
                        }
                    }
                }
            }
            if (count > 0) {
                plugin.getLogger().info(String.format(
                    "SpringRegeneration: Applied bone meal to %d blocks in world '%s'",
                    count, world.getName()
                ));
            }
        }
    }
}