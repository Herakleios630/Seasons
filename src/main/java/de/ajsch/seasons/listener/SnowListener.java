package de.ajsch.seasons.listener;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.weather.SnowAccumulator;
import de.ajsch.seasons.weather.SnowGrower;
import de.ajsch.seasons.weather.WeatherConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SnowListener implements Listener {

    private final JavaPlugin plugin;
    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final SnowAccumulator accumulator;
    private final SnowGrower snowGrower;
    private final WeatherConfig weatherConfig;

    public SnowListener(JavaPlugin plugin, SeasonClock clock, TemperatureCalculator tempCalc,
                        SnowAccumulator accumulator, WeatherConfig weatherConfig) {
        this.plugin = plugin;
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.accumulator = accumulator;
        this.snowGrower = accumulator.getSnowGrower();
        this.weatherConfig = weatherConfig;
    }

    @EventHandler
    public void onSnowForm(BlockFormEvent event) {
        if (event.getNewState().getType() != Material.SNOW) return;

        Block block = event.getBlock();
        org.bukkit.block.Biome biome = block.getBiome();
        double temp = tempCalc.calculate(clock.calculateDayOfYear(), biome);
        int maxHeight = snowGrower.getMaxSnowHeight(temp);

        if (block.getType() == Material.SNOW) {
            Snow snow = (Snow) block.getBlockData();
            if (snow.getLayers() >= maxHeight) {
                event.setCancelled(true);
                return;
            }
        }

        if (clock.getCurrentSeason() != Season.WINTER) {
            if (temp > 0.0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSnowMelt(BlockFadeEvent event) {
        if (!weatherConfig.isEnabled()) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SNOW) return;

        org.bukkit.block.Biome biome = block.getBiome();
        double temp = tempCalc.calculate(clock.calculateDayOfYear(), biome);

        if (temp >= weatherConfig.getMeltThreshold()) {
            Snow snow = (Snow) block.getBlockData();
            int currentLayers = snow.getLayers();
            int meltSpeed = weatherConfig.getMeltSpeed();

            boolean willDisappear = currentLayers <= meltSpeed;

            if (willDisappear) {
                block.setType(Material.AIR, false);
            } else {
                snow.setLayers(currentLayers - meltSpeed);
                block.setBlockData(snow, false);
            }
            event.setCancelled(true);

            // BoneMeal-Regeneration: wenn Schnee verschwindet und darunter GRASS_BLOCK liegt
            if (willDisappear && weatherConfig.getSnowMeltBonemeal()) {
                Block below = block.getRelative(BlockFace.DOWN);
                if (below.getType() == Material.GRASS_BLOCK) {
                    below.applyBoneMeal(BlockFace.UP);
                }
            }
        }
    }
}