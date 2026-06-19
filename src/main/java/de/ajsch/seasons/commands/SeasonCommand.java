package de.ajsch.seasons.commands;

import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SeasonCommand implements CommandExecutor {

    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final BiomeTemperature biomeTemp;

    public SeasonCommand(SeasonClock clock, TemperatureCalculator tempCalc, BiomeTemperature biomeTemp) {
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.biomeTemp = biomeTemp;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Season current = clock.getCurrentSeason();
        int day = clock.calculateDayOfYear();
        int daysRemaining = clock.getDaysRemainingInSeason();
        int year = clock.getYear();
        double temp = 0.0;
        String biomeName = "N/A";
        String category = "N/A";

        if (sender instanceof Player player) {
            org.bukkit.block.Biome biome = player.getLocation().getBlock().getBiome();
            biomeName = biome.getKey().getKey();
            temp = tempCalc.calculate(day, biome);
            category = biomeTemp.getCategory(biome).name();
        }

        sender.sendMessage(Component.text("=== Seasons ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Jahr ", NamedTextColor.GRAY)
            .append(Component.text(year, NamedTextColor.WHITE))
            .append(Component.text(" | Tag ", NamedTextColor.GRAY))
            .append(Component.text(day, NamedTextColor.WHITE))
            .append(Component.text(" | ", NamedTextColor.GRAY))
            .append(Component.text(current.getDisplayName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Noch ", NamedTextColor.GRAY)
            .append(Component.text(daysRemaining, NamedTextColor.WHITE))
            .append(Component.text(" Tage bis zum nächsten Season-Wechsel", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Temperatur: ", NamedTextColor.GRAY)
            .append(Component.text(String.format("%.2f", temp), NamedTextColor.WHITE))
            .append(Component.text(" | Biom: ", NamedTextColor.GRAY))
            .append(Component.text(biomeName, NamedTextColor.WHITE))
            .append(Component.text(" (" + category + ")", NamedTextColor.GRAY)));
        if (tempCalc.hasOverride()) {
            sender.sendMessage(Component.text("Temperatur-Override aktiv!", NamedTextColor.RED));
        }
        return true;
    }
}