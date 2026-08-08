package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.utils.SlimeFormat;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.locale.Messages;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class VersionCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "version";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.version.description");
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        sender.sendMessage(Messages.prefixed("version.message-full",
                SWMPlugin.getInstance().getDescription().getVersion(),
                SlimeFormat.SLIME_VERSION));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
