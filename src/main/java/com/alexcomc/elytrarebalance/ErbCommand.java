package com.alexcomc.elytrarebalance;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class ErbCommand implements CommandExecutor, TabCompleter {

    private final ElytraRebalance plugin;

    public ErbCommand(ElytraRebalance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§eUso: /erb reload");
            return true;
        }

        if (!sender.hasPermission("elytrarebalance.reload")) {
            sender.sendMessage(plugin.getMessagesManager().get("general.no-permission", "&cNo tienes permiso para usar este comando."));
            return true;
        }

        plugin.reloadAll();
        sender.sendMessage(plugin.getMessagesManager().get("general.reload-success", "&aConfiguración recargada."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
