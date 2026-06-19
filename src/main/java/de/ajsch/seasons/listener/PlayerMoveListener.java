package de.ajsch.seasons.listener;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.weather.PrecipitationCategory;
import de.ajsch.seasons.weather.WeatherConfig;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {

    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final BiomeTemperature biomeTemp;
    private final WeatherConfig weatherConfig;

    public PlayerMoveListener(SeasonClock clock, TemperatureCalculator tempCalc,
                              BiomeTemperature biomeTemp, WeatherConfig weatherConfig) {
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.biomeTemp = biomeTemp;
        this.weatherConfig = weatherConfig;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!weatherConfig.isEnabled()) return;

        Biome from = event.getFrom().getBlock().getBiome();
        Biome to = event.getTo().getBlock().getBiome();
        if (from == to) return;

        PrecipitationCategory fromCat = biomeTemp.getCategory(from);
        PrecipitationCategory toCat = biomeTemp.getCategory(to);

        if (fromCat == toCat) return;

        boolean isWinter = clock.getCurrentSeason() == Season.WINTER;
        double temp = tempCalc.calculate(clock.calculateDayOfYear(), to);

        if (isWinter && toCat == PrecipitationCategory.CAN_FREEZE && temp < weatherConfig.getFreezeThreshold()) {
            event.getPlayer().setPlayerWeather(org.bukkit.WeatherType.CLEAR);
        } else if (fromCat == PrecipitationCategory.CAN_FREEZE) {
            event.getPlayer().resetPlayerWeather();
        }
    }
}