package com.grinderwolf.swm.plugin.commands.sub;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.locale.Messages;
import com.grinderwolf.swm.plugin.log.Logging;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeleteWorldCmd implements Subcommand {

    private final Cache<String, String[]> deleteCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public String getUsage() {
        return "delete <world> [data-source]";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.delete.description");
    }

    @Override
    public String getPermission() {
        return "swm.deleteworld";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            String worldName = args[0];
            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                sender.sendMessage(Messages.prefixed("delete.loaded", worldName));
                return true;
            }

            String source;

            if (args.length > 1) {
                source = args[1];
            } else {
                WorldsConfig config = ConfigManager.getWorldConfig();
                WorldData worldData = config.getWorlds().get(worldName);

                if (worldData == null) {
                    sender.sendMessage(Messages.prefixed("delete.unknown-world", worldName));
                    return true;
                }

                source = worldData.getDataSource();
            }

            SlimeLoader loader = LoaderUtils.getLoader(source);

            if (loader == null) {
                sender.sendMessage(Messages.prefixed("delete.unknown-source", source));
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(Messages.prefixed("common.world-in-use", worldName));
                return true;
            }

            String[] oldArgs = deleteCache.getIfPresent(sender.getName());

            if (oldArgs != null) {
                deleteCache.invalidate(sender.getName());

                if (Arrays.equals(args, oldArgs)) { // Make sure it's exactly the same command
                    sender.sendMessage(Messages.prefixed("delete.deleting", worldName));

                    // No need to do this synchronously
                    CommandManager.getInstance().getWorldsInUse().add(worldName);
                    Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {

                        try {
                            if (loader.isWorldLocked(worldName)) {
                                sender.sendMessage(Messages.prefixed("delete.in-use", worldName));
                                return;
                            }

                            long start = System.currentTimeMillis();
                            loader.deleteWorld(worldName);

                            // Now let's delete it from the config file
                            WorldsConfig config = ConfigManager.getWorldConfig();

                            config.getWorlds().remove(worldName);
                            config.save();

                            sender.sendMessage(Messages.prefixed("delete.success", worldName, System.currentTimeMillis() - start));
                        } catch (IOException ex) {
                            if (!(sender instanceof ConsoleCommandSender)) {
                                sender.sendMessage(Messages.prefixed("delete.failed-io", worldName));
                            }

                            Logging.error("Failed to delete world " + worldName + ". Stack trace:");
                            ex.printStackTrace();
                        } catch (UnknownWorldException ex) {
                            sender.sendMessage(Messages.prefixed("delete.not-in-source", source, worldName));
                        } finally {
                            CommandManager.getInstance().getWorldsInUse().remove(worldName);
                        }

                    });

                    return true;
                }
            }

            sender.sendMessage(Messages.prefixed("delete.warning", worldName));
            sender.sendMessage(" ");
            sender.sendMessage(Messages.get("delete.warning-confirm"));

            deleteCache.put(sender.getName(), args);

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> toReturn = null;
        final String typed = args.length > 1 ? args[1].toLowerCase() : "";

        if (args.length == 2) {
            for (World world : Bukkit.getWorlds()) {
                final String worldName = world.getName();

                if (worldName.toLowerCase().startsWith(typed)) {
                    if (toReturn == null) {
                        toReturn = new LinkedList<>();
                    }
                    toReturn.add(worldName);
                }
            }
            return toReturn == null ? Collections.emptyList() : toReturn;
        }

        if (args.length == 3) {
            toReturn = new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
        }

        if (args.length == 4) {
            toReturn = new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
        }

        return toReturn == null ? Collections.emptyList() : toReturn;
    }
}
