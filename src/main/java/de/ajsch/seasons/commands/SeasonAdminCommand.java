package de.ajsch.seasons.commands;

import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.visual.BiomeJsonGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SeasonAdminCommand implements CommandExecutor {

    private final SeasonClock clock;
    private final TemperatureCalculator tempCalc;
    private final ConfigManager configManager;
    private final BiomeTemperature biomeTemp;
    private final BiomeJsonGenerator biomeJsonGenerator;

    public SeasonAdminCommand(SeasonClock clock, TemperatureCalculator tempCalc,
                              ConfigManager configManager, BiomeTemperature biomeTemp,
                              BiomeJsonGenerator biomeJsonGenerator) {
        this.clock = clock;
        this.tempCalc = tempCalc;
        this.configManager = configManager;
        this.biomeTemp = biomeTemp;
        this.biomeJsonGenerator = biomeJsonGenerator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        return switch (args[0].toLowerCase()) {
            case "debug" -> handleDebug(sender);
            case "skip" -> handleSkip(sender, args);
            case "set" -> handleSet(sender, args);
            case "speed" -> handleSpeed(sender, args);
            case "temp" -> handleTemp(sender, args);
            case "reload" -> handleReload(sender);
            case "generate-biomes" -> handleGenerateBiomes(sender, args);
            default -> false;
        };
    }

    private boolean handleTemp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("/season temp <wert|reset> – Temperatur-Override", NamedTextColor.RED));
            return true;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            tempCalc.resetOverride();
            sender.sendMessage(Component.text("Temperatur-Override zuruckgesetzt.", NamedTextColor.GREEN));
            return true;
        }
        try {
            double temp = Double.parseDouble(args[1]);
            tempCalc.setOverride(temp);
            sender.sendMessage(Component.text("Temperatur-Override auf " + String.format("%.2f", temp) + " gesetzt.", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Ungultiger Temperaturwert: " + args[1], NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        sender.sendMessage(Component.text("=== Seasons Debug ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Debug-Mode: ", NamedTextColor.GRAY)
            .append(Component.text(configManager.isDebugMode() ? "AN" : "AUS", configManager.isDebugMode() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("Year-Start-Offset: ", NamedTextColor.GRAY)
            .append(Component.text(clock.getDataStore().getYearStartOffset(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("FullTime (Welt): ", NamedTextColor.GRAY)
            .append(Component.text(clock.getWorld().getFullTime(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Speed-Multiplikator: ", NamedTextColor.GRAY)
            .append(Component.text(clock.getSpeedMultiplier(), NamedTextColor.WHITE)));
        if (sender instanceof Player player) {
            org.bukkit.block.Biome biome = player.getLocation().getBlock().getBiome();
            double temp = tempCalc.calculate(clock.calculateDayOfYear(), biome);
            sender.sendMessage(Component.text("Temperatur-Rohwert: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.4f", temp), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("Biom-Kategorie: ", NamedTextColor.GRAY)
                .append(Component.text(biomeTemp.getCategory(biome).name(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("Freeze-Threshold: ", NamedTextColor.GRAY)
                .append(Component.text(configManager.getFreezeThreshold(), NamedTextColor.WHITE)));
            if (tempCalc.hasOverride()) {
                sender.sendMessage(Component.text("Override aktiv", NamedTextColor.RED));
            }
        }
        return true;
    }

    private boolean handleSkip(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("/season skip <days>", NamedTextColor.RED));
            return true;
        }
        try {
            String arg = args[1].trim();
            if (arg.startsWith("+")) arg = arg.substring(1);
            int days = Integer.parseInt(arg);
            if (days <= 0) {
                sender.sendMessage(Component.text("Tageszahl muss positiv sein.", NamedTextColor.RED));
                return true;
            }
            clock.skipDays(days);
            sender.sendMessage(Component.text(days + " Tage ubersprungen.", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Ungultige Tageszahl: " + args[1], NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("/season set <SPRING|SUMMER|FALL|WINTER>", NamedTextColor.RED));
            return true;
        }
        try {
            Season target = Season.valueOf(args[1].toUpperCase());
            clock.setSeason(target);
            sender.sendMessage(Component.text("Season gesetzt auf " + target.getDisplayName() + ".", NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unbekannte Season: " + args[1] + ". Nutze SPRING, SUMMER, FALL oder WINTER.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("/season speed <multiplier>", NamedTextColor.RED));
            return true;
        }
        try {
            double speed = Double.parseDouble(args[1]);
            if (speed <= 0) {
                sender.sendMessage(Component.text("Multiplikator muss > 0 sein.", NamedTextColor.RED));
                return true;
            }
            clock.setSpeedMultiplier(speed);
            sender.sendMessage(Component.text("Speed-Multiplikator auf " + speed + " gesetzt.", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Ungultiger Multiplikator: " + args[1], NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        configManager.reload();
        biomeTemp.reload();
        sender.sendMessage(Component.text("Configs neu geladen.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGenerateBiomes(CommandSender sender, String[] args) {
        if (!sender.hasPermission("seasons.admin")) {
            sender.sendMessage(Component.text("Keine Berechtigung.", NamedTextColor.RED));
            return true;
        }

        boolean force = false;
        if (args.length > 1 && args[1].equalsIgnoreCase("force")) {
            force = true;
        }

        sender.sendMessage(Component.text("Generiere Custom-Biome-JSONs...", NamedTextColor.YELLOW));
        int count = biomeJsonGenerator.generate(force);

        if (count == 0) {
            sender.sendMessage(Component.text(
                    "Keine JSONs generiert (Config unverandert oder Fehler). Nutze /season generate-biomes force fur Neugenerierung.",
                    NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text(count + " Biome-JSONs generiert.", NamedTextColor.GREEN));
        }
        return true;
    }
}