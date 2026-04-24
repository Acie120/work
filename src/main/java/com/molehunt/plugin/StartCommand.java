package com.molehunt.plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StartCommand implements CommandExecutor {

    private final MoleHuntPlugin plugin;

    public StartCommand(MoleHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can start MoleHunt!");
            return true;
        }

        if (!sender.hasPermission("molehunt.start")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to start MoleHunt.");
            return true;
        }

        Player operator = (Player) sender;
        plugin.getGameManager().startGame(operator);
        return true;
    }
}
