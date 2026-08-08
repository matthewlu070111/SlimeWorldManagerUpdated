package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.plugin.locale.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class UnloadWorldCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "unload <world>";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.unload.description");
    }

    @Override
    public String getPermission() {
        return "swm.unloadworld";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            World world = Bukkit.getWorld(args[0]);

            if (world == null) {
                sender.sendMessage(Messages.prefixed("common.world-not-loaded", args[0]));
                return true;
            }

            // Teleport all players outside the world before unloading it
            List<Player> players = world.getPlayers();

            if (!players.isEmpty()) {
                World defaultWorld = Bukkit.getWorlds().get(0);
                Location spawnLocation = defaultWorld.getSpawnLocation();

                while (spawnLocation.getBlock().getType() != Material.AIR || spawnLocation.getBlock().getRelative(BlockFace.UP).getType() != Material.AIR) {
                    spawnLocation.add(0, 1, 0);
                }

                for (Player player : players) {
                    player.teleport(spawnLocation);
                }
            }

            if (Bukkit.unloadWorld(world, true)) {
                sender.sendMessage(Messages.prefixed("unload.success", args[0]));
            } else {
                sender.sendMessage(Messages.prefixed("unload.failed", args[0]));
            }

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> toReturn = null;

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

        return toReturn == null ? Collections.emptyList() : toReturn;
    }
}
