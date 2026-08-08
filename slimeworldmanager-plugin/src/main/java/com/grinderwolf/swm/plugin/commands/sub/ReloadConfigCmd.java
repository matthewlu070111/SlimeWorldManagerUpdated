package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.locale.Messages;
import com.grinderwolf.swm.plugin.log.Logging;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ReloadConfigCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.reload.description");
    }

    @Override
    public String getPermission() {
        return "swm.reload";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        try {
            ConfigManager.initialize();
            Messages.initialize(ConfigManager.getMainConfig().getLanguage());
        } catch (IOException | ObjectMappingException ex) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(Messages.prefixed("reload.failed"));
            }

            Logging.error("Failed to load config files:");
            ex.printStackTrace();

            return true;
        }

        sender.sendMessage(Messages.prefixed("reload.success"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
