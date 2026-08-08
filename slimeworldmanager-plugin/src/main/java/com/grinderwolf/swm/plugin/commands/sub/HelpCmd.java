package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.locale.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class HelpCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "help";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.help.description");
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        sender.sendMessage(Messages.prefixed("command.help-header"));

        for (Subcommand cmd : CommandManager.getInstance().getCommands()) {
            if (cmd.inGameOnly() && !(sender instanceof Player) || (!cmd.getPermission().equals("") && !sender.hasPermission(cmd.getPermission()) && !sender.hasPermission("swm.*"))) {
                continue;
            }

            sender.sendMessage(Messages.get("command.help-entry", cmd.getUsage(), cmd.getDescription()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
