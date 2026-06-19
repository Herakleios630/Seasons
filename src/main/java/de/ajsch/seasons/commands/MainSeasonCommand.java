package de.ajsch.seasons.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MainSeasonCommand implements CommandExecutor {

    private final SeasonCommand infoCommand;
    private final SeasonAdminCommand adminCommand;

    public MainSeasonCommand(SeasonCommand infoCommand, SeasonAdminCommand adminCommand) {
        this.infoCommand = infoCommand;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return infoCommand.onCommand(sender, command, label, args);
        }
        return adminCommand.onCommand(sender, command, label, args);
    }
}