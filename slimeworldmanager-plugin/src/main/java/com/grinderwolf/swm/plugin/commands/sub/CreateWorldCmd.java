package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.locale.Messages;
import com.grinderwolf.swm.plugin.log.Logging;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CreateWorldCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "create <world> <data-source>";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.create.description");
    }

    @Override
    public String getPermission() {
        return "swm.createworld";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String worldName = args[0];

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(Messages.prefixed("common.world-in-use", worldName));
                return true;
            }

            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                sender.sendMessage(Messages.prefixed("create.already-exists-bukkit", worldName));
                return true;
            }

            WorldsConfig config = ConfigManager.getWorldConfig();

            if (config.getWorlds().containsKey(worldName)) {
                sender.sendMessage(Messages.prefixed("create.already-in-config", worldName));
                return true;
            }

            String dataSource = args[1];
            SlimeLoader loader = SWMPlugin.getInstance().getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(Messages.prefixed("common.unknown-data-source", dataSource));
                return true;
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);
            sender.sendMessage(Messages.prefixed("create.creating", worldName));

            // It's best to load the world async, and then just go back to the server thread and add it to the world list
            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {

                try {
                    long start = System.currentTimeMillis();

                    WorldData worldData = new WorldData();
                    worldData.setDataSource(dataSource);
                    worldData.setSpawn("0, 64, 0");

                    SlimePropertyMap propertyMap = worldData.toPropertyMap();
                    SlimeWorld slimeWorld = SWMPlugin.getInstance().createEmptyWorld(loader, worldName, false, propertyMap);

                    Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                        try {
                            SWMPlugin.getInstance().generateWorld(slimeWorld);

                            // Bedrock block
                            Location location = new Location(Bukkit.getWorld(worldName), 0, 61, 0);
                            location.getBlock().setType(Material.BEDROCK);

                            // Config (source + defaults so load/unload/goto work after restart)
                            config.registerIfAbsent(worldName, worldData);

                            sender.sendMessage(Messages.prefixed("create.success", worldName, System.currentTimeMillis() - start));
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(Messages.prefixed("create.failed", worldName, ex.getMessage()));
                        }
                    });
                } catch (WorldAlreadyExistsException ex) {
                    sender.sendMessage(Messages.prefixed("create.failed-exists", worldName, dataSource));
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender)) {
                        sender.sendMessage(Messages.prefixed("create.failed-io", worldName));
                    }

                    Logging.error("Failed to load world " + worldName + ":");
                    ex.printStackTrace();
                } finally {
                    CommandManager.getInstance().getWorldsInUse().remove(worldName);
                }
            });

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
