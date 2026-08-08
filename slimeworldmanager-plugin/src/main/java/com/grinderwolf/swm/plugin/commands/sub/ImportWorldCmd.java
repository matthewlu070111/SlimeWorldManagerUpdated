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
import com.grinderwolf.swm.plugin.locale.Messages;
import com.grinderwolf.swm.plugin.log.Logging;
import org.bukkit.Bukkit;
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

public class ImportWorldCmd implements Subcommand {

    private final Cache<String, String[]> importCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public String getUsage() {
        return "import <path-to-world> <data-source> [new-world-name]";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.import.description");
    }

    @Override
    public String getPermission() {
        return "swm.importworld";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String dataSource = args[1];
            SlimeLoader loader = LoaderUtils.getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(Messages.prefixed("import.source-missing", dataSource));
                return true;
            }

            File worldDir = new File(args[0]);

            if (!worldDir.exists() || !worldDir.isDirectory()) {
                sender.sendMessage(Messages.prefixed("import.invalid-path", worldDir.getPath()));
                return true;
            }

            String[] oldArgs = importCache.getIfPresent(sender.getName());

            if (oldArgs != null) {
                importCache.invalidate(sender.getName());

                if (Arrays.equals(args, oldArgs)) { // Make sure it's exactly the same command
                    String worldName = (args.length > 2 ? args[2] : worldDir.getName());

                    World alreadyLoaded = Bukkit.getWorld(worldName);
                    if (alreadyLoaded != null) {
                        sender.sendMessage(Messages.prefixed("common.world-already-loaded", worldName));
                        return true;
                    }

                    if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                        sender.sendMessage(Messages.prefixed("common.world-in-use", worldName));
                        return true;
                    }

                    CommandManager.getInstance().getWorldsInUse().add(worldName);
                    sender.sendMessage(Messages.prefixed("import.importing", worldDir.getName(), dataSource));

                    Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {
                        try {
                            long start = System.currentTimeMillis();
                            SWMPlugin.getInstance().importWorld(worldDir, worldName, loader);

                            // Register so /swm load works without hand-editing worlds.yml
                            WorldData worldData = new WorldData();
                            worldData.setDataSource(dataSource);
                            worldData.setLoadOnStartup(false); // large maps should not force-load on restart unless auto_load_all_worlds
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
                                    sender.sendMessage(Messages.prefixed("import.success", worldName,
                                            System.currentTimeMillis() - start,
                                            finalRegistered ? Messages.get("import.registered-suffix") : ""));
                                } catch (IllegalArgumentException ex) {
                                    sender.sendMessage(Messages.prefixed("import.imported-load-failed", worldName, ex.getMessage()));
                                    sender.sendMessage(Messages.prefixed("import.use-load-later", worldName));
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
                                        sender.sendMessage(Messages.prefixed("import.already-existed-loaded", worldName,
                                                finalRegistered ? Messages.get("import.registered-suffix") : "",
                                                System.currentTimeMillis() - start));
                                    } catch (IllegalArgumentException genEx) {
                                        sender.sendMessage(Messages.prefixed("import.already-existed-generate-failed", worldName,
                                                finalRegistered ? "; registered in worlds.yml. " : ". ",
                                                genEx.getMessage()));
                                    }
                                });
                            } catch (Exception loadEx) {
                                sender.sendMessage(Messages.prefixed("import.already-existed", dataSource, worldName,
                                        registered ? Messages.get("import.registered-try-load", worldName) : ""));
                            }
                        } catch (InvalidWorldException ex) {
                            sender.sendMessage(Messages.prefixed("import.invalid-world", worldDir.getName()));
                        } catch (WorldLoadedException ex) {
                            sender.sendMessage(Messages.prefixed("import.world-loaded", worldDir.getName()));
                        } catch (WorldTooBigException ex) {
                            sender.sendMessage(Messages.prefixed("import.too-big"));
                        } catch (CorruptedWorldException ex) {
                            sender.sendMessage(Messages.prefixed("import.loaded-corrupted", worldName));
                            Logging.error("Failed to load imported world " + worldName + ": world seems to be corrupted.");
                            ex.printStackTrace();
                        } catch (NewerFormatException ex) {
                            sender.sendMessage(Messages.prefixed("import.loaded-newer", worldName, ex.getMessage()));
                        } catch (UnknownWorldException ex) {
                            sender.sendMessage(Messages.prefixed("import.loaded-unknown", worldName, dataSource));
                        } catch (WorldInUseException ex) {
                            sender.sendMessage(Messages.prefixed("import.loaded-in-use", worldName));
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(Messages.prefixed("import.loaded-failed", worldName, ex.getMessage()));
                        } catch (IOException ex) {
                            if (!(sender instanceof ConsoleCommandSender)) {
                                sender.sendMessage(Messages.prefixed("import.failed-io", worldName));
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

            sender.sendMessage(Messages.prefixed("import.warning"));
            sender.sendMessage(" ");
            sender.sendMessage(Messages.prefixed("import.note"));
            sender.sendMessage(" ");
            sender.sendMessage(Messages.prefixed("import.confirm"));

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
