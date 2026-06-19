package de.ajsch.seasons.listener;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.weather.PrecipitationCategory;
import de.ajsch.seasons.weather.WeatherConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final BiomeTemperature biomeTemp;
    private final WeatherConfig weatherConfig;

    public PlayerJoinListener(SeasonClock clock, TemperatureCalculator tempCalc,
                              BiomeTemperature biomeTemp, WeatherConfig weatherConfig) {
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.biomeTemp = biomeTemp;
        this.weatherConfig = weatherConfig;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Season current = clock.getCurrentSeason();
        int day = clock.calculateDayOfYear();
        int year = clock.getYear();
        event.getPlayer().sendMessage(Component.text("Seasons", NamedTextColor.GOLD)
            .append(Component.text(" | ", NamedTextColor.GRAY))
            .append(Component.text(current.getDisplayName(), NamedTextColor.WHITE))
            .append(Component.text(" · Jahr ", NamedTextColor.GRAY))
            .append(Component.text(year, NamedTextColor.WHITE))
            .append(Component.text(" · Tag ", NamedTextColor.GRAY))
            .append(Component.text(day, NamedTextColor.WHITE)));

        if (current == Season.WINTER && weatherConfig.isEnabled()) {
            org.bukkit.block.Biome biome = event.getPlayer().getLocation().getBlock().getBiome();
            PrecipitationCategory category = biomeTemp.getCategory(biome);
            double temp = tempCalc.calculate(day, biome);
            if (category == PrecipitationCategory.CAN_FREEZE && temp < weatherConfig.getFreezeThreshold()) {
                event.getPlayer().setPlayerWeather(org.bukkit.WeatherType.CLEAR);
            }
        }
    }
}