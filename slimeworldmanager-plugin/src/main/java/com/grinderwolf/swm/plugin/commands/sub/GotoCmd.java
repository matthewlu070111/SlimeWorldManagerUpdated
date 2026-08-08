package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.locale.Messages;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GotoCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "goto <world> [player]";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.goto.description");
    }

    @Override
    public String getPermission() {
        return "swm.goto";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            World world = Bukkit.getWorld(args[0]);

            if (world == null) {
                sender.sendMessage(Messages.prefixed("goto.world-missing", args[0]));
                return true;
            }

            Player target;

            if (args.length > 1) {
                target = Bukkit.getPlayerExact(args[1]);
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Messages.prefixed("goto.console-needs-player"));
                    return true;
                }

                target = (Player) sender;
            }

            if (target == null) {
                sender.sendMessage(Messages.prefixed("goto.player-offline", args[1]));
                return true;
            }

            if (target.getName().equals(sender.getName())) {
                sender.sendMessage(Messages.prefixed("goto.teleporting-self", world.getName()));
            } else {
                sender.sendMessage(Messages.prefixed("goto.teleporting-other", target.getName(), world.getName()));
            }

            Location spawnLocation = world.getSpawnLocation();

            // Safe Spawn Location
            while (spawnLocation.getBlock().getType() != Material.AIR || spawnLocation.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
                spawnLocation.add(0, 1, 0);
            }

            target.teleport(spawnLocation);

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> toReturn = null;

        if (sender instanceof ConsoleCommandSender) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            final String typed = args[1].toLowerCase();

            for (World world : Bukkit.getWorlds()) {
                final String worldName = world.getName();
                if (worldName.toLowerCase().startsWith(typed)) {
                    if (toReturn == null) {
                        toReturn = new LinkedList<>();
                    }
                    toReturn.add(worldName);
                }
            }
        }

        if (args.length == 3) {
            final String typed = args[2].toLowerCase();

            for (Player player : Bukkit.getOnlinePlayers()) {
                final String playerName = player.getName();
                if (playerName.toLowerCase().startsWith(typed)) {
                    if (toReturn == null) {
                        toReturn = new LinkedList<>();
                    }
                    toReturn.add(playerName);
                }
            }
        }

        return toReturn == null ? Collections.emptyList() : toReturn;
    }
}
