package com.grinderwolf.swm.plugin.commands.sub;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.grinderwolf.swm.api.exceptions.CorruptedWorldException;
import com.grinderwolf.swm.api.exceptions.InvalidWorldException;
import com.grinderwolf.swm.api.exceptions.NewerFormatException;
import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.exceptions.WorldInUseException;
import com.grinderwolf.swm.api.exceptions.WorldLoadedException;
import com.grinderwolf.swm.api.exceptions.WorldTooBigException;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.log.Logging;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
public class ImportWorldCmd implements Subcommand {

    private final String usage = "import <path-to-world> <data-source> [new-world-name]";
    private final String description = "Convert a world to the slime format and save it.";
    private final String permission = "swm.importworld";

    private final Cache<String, String[]> importCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String dataSource = args[1];
            SlimeLoader loader = LoaderUtils.getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Data source " + dataSource + " does not exist.");

                return true;
            }

            File worldDir = new File(args[0]);

            if (!worldDir.exists() || !worldDir.isDirectory()) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Path " + worldDir.getPath() + " does not point out to a valid world directory.");

                return true;
            }

            String[] oldArgs = importCache.getIfPresent(sender.getName());

            if (oldArgs != null) {
                importCache.invalidate(sender.getName());

                if (Arrays.equals(args, oldArgs)) { // Make sure it's exactly the same command
                    String worldName = (args.length > 2 ? args[2] : worldDir.getName());

                    World alreadyLoaded = Bukkit.getWorld(worldName);
                    if (alreadyLoaded != null) {
                        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already loaded!");
                        return true;
                    }

                    if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " is already being used on another command! Wait some time and try again.");
                        return true;
                    }

                    CommandManager.getInstance().getWorldsInUse().add(worldName);
                    sender.sendMessage(Logging.COMMAND_PREFIX + "Importing world " + worldDir.getName() + " into data source " + dataSource + "...");

                    Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                        try {
                            long start = System.currentTimeMillis();
                            SWMPlugin.getInstance().importWorld(worldDir, worldName, loader);

                            // Register so /swm load works without hand-editing worlds.yml
                            WorldData worldData = new WorldData();
                            worldData.setDataSource(dataSource);
                            worldData.setLoadOnStartup(false); // large maps should not force-load on restart
                            WorldsConfig config = ConfigManager.getWorldConfig();
                            boolean registered = config.registerIfAbsent(worldName, worldData);
                            // If already present, use existing settings (may have custom spawn etc.)
                            WorldData loadData = config.getWorlds().get(worldName);
                            if (loadData == null) {
                                loadData = worldData;
                            }

                            // Auto-load so goto/unload work immediately
                            SlimeLoader loadLoader = SWMPlugin.getInstance().getLoader(loadData.getDataSource());
                            if (loadLoader == null) {
                                loadLoader = loader;
                            }

                            SlimeWorld slimeWorld = SWMPlugin.getInstance().loadWorld(
                                    loadLoader, worldName, loadData.isReadOnly(), loadData.toPropertyMap());

                            boolean finalRegistered = registered;
                            Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                                try {
                                    SWMPlugin.getInstance().generateWorld(slimeWorld);
                                    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
                                            + ChatColor.GREEN + " imported and loaded in " + (System.currentTimeMillis() - start) + "ms!"
                                            + (finalRegistered ? ChatColor.GRAY + " (registered in worlds.yml)" : ""));
                                } catch (IllegalArgumentException ex) {
                                    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
                                            + ChatColor.GREEN + " imported successfully, but failed to load: " + ex.getMessage() + ".");
                                    sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GRAY + "It is registered in worlds.yml — use "
                                            + ChatColor.YELLOW + "/swm load " + worldName + ChatColor.GRAY + " later.");
                                }
                            });
                        } catch (WorldAlreadyExistsException ex) {
                            // Slime already stored (e.g. earlier import without worlds.yml). Register + try load.
                            WorldsConfig config = ConfigManager.getWorldConfig();
                            WorldData worldData = new WorldData();
                            worldData.setDataSource(dataSource);
                            worldData.setLoadOnStartup(false);
                            boolean registered = config.registerIfAbsent(worldName, worldData);
                            WorldData loadData = config.getWorlds().get(worldName);
                            if (loadData == null) {
                                loadData = worldData;
                            }

                            try {
                                long start = System.currentTimeMillis();
                                SlimeLoader loadLoader = SWMPlugin.getInstance().getLoader(loadData.getDataSource());
                                if (loadLoader == null) {
                                    loadLoader = loader;
                                }
                                SlimeWorld slimeWorld = SWMPlugin.getInstance().loadWorld(
                                        loadLoader, worldName, loadData.isReadOnly(), loadData.toPropertyMap());
                                boolean finalRegistered = registered;
                                Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                                    try {
                                        SWMPlugin.getInstance().generateWorld(slimeWorld);
                                        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + worldName
                                                + ChatColor.GREEN + " already existed in the data source and is now loaded"
                                                + (finalRegistered ? ChatColor.GRAY + " (registered in worlds.yml)" : "")
                                                + ChatColor.GREEN + " (" + (System.currentTimeMillis() - start) + "ms).");
                                    } catch (IllegalArgumentException genEx) {
                                        sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Data source already has " + worldName
                                                + (finalRegistered ? "; registered in worlds.yml. " : ". ")
                                                + "Failed to generate: " + genEx.getMessage());
                                    }
                                });
                            } catch (Exception loadEx) {
                                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Data source " + dataSource
                                        + " already contains a world called " + worldName + "."
                                        + (registered ? ChatColor.GRAY + " Registered in worlds.yml — try /swm load " + worldName + "." : ""));
                            }
                        } catch (InvalidWorldException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Directory " + worldDir.getName() + " does not contain a valid Minecraft world.");
                        } catch (WorldLoadedException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldDir.getName() + " is loaded on this server. Please unload it before importing it.");
                        } catch (WorldTooBigException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Hey! Didn't you just read the warning? The Slime Format isn't meant for big worlds." +
                                    " The world you provided just breaks everything. Please, trim it by using the MCEdit tool and try again.");
                        } catch (CorruptedWorldException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " was imported but could not be loaded: world seems to be corrupted.");
                            Logging.error("Failed to load imported world " + worldName + ": world seems to be corrupted.");
                            ex.printStackTrace();
                        } catch (NewerFormatException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " was imported but could not be loaded: newer Slime Format (" + ex.getMessage() + ").");
                        } catch (UnknownWorldException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " was imported but could not be found in data source '" + dataSource + "'.");
                        } catch (WorldInUseException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " was imported but is already in use. Wait and try /swm load " + worldName + ".");
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + worldName + " was imported but failed to load: " + ex.getMessage());
                        } catch (IOException ex) {
                            if (!(sender instanceof ConsoleCommandSender)) {
                                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "Failed to import world " + worldName
                                        + ". Take a look at the server console for more information.");
                            }

                            Logging.error("Failed to import world " + worldName + ". Stack trace:");
                            ex.printStackTrace();
                        } finally {
                            CommandManager.getInstance().getWorldsInUse().remove(worldName);
                        }
                    });

                    return true;
                }
            }

            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + ChatColor.BOLD + "WARNING: " + ChatColor.GRAY + "The Slime Format is meant to " +
                    "be used on tiny maps, not big survival worlds. It is recommended to trim your world by using the Prune MCEdit tool to ensure " +
                    "you don't save more chunks than you want to.");

            sender.sendMessage(" ");
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.YELLOW + ChatColor.BOLD + "NOTE: " + ChatColor.GRAY + "This command will automatically ignore every " +
                    "chunk that doesn't contain any blocks.");
            sender.sendMessage(" ");
            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GRAY + "If you are sure you want to continue, type again this command.");

            importCache.put(sender.getName(), args);

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 3) {
            return new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
        }

        return Collections.emptyList();
    }
}
