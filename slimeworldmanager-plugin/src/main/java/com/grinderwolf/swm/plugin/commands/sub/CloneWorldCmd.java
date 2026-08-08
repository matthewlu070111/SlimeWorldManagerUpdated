package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.exceptions.*;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.*;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.locale.Messages;
import com.grinderwolf.swm.plugin.log.Logging;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CloneWorldCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "clone-world <template-world> <world-name> [new-data-source]";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.clone-world.description");
    }

    @Override
    public String getPermission() {
        return "swm.cloneworld";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String worldName = args[1];
            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                sender.sendMessage(Messages.prefixed("common.world-already-loaded", worldName));
                return true;
            }

            String templateWorldName = args[0];

            WorldsConfig config = ConfigManager.getWorldConfig();
            WorldData worldData = config.getWorlds().get(templateWorldName);

            if (worldData == null) {
                sender.sendMessage(Messages.prefixed("common.world-not-in-config", templateWorldName));
                return true;
            }

            if (templateWorldName.equals(worldName)) {
                sender.sendMessage(Messages.prefixed("clone.template-same-name"));
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(Messages.prefixed("common.world-in-use", worldName));
                return true;
            }

            String dataSource = args.length > 2 ? args[2] : worldData.getDataSource();
            SlimeLoader loader = SWMPlugin.getInstance().getLoader(dataSource);

            if (loader == null) {
                sender.sendMessage(Messages.prefixed("common.unknown-data-source", dataSource));
                return true;
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);
            sender.sendMessage(Messages.prefixed("clone.creating", worldName, templateWorldName));

            // It's best to load the world async, and then just go back to the server thread and add it to the world list
            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {

                try {
                    long start = System.currentTimeMillis();

                    SlimeWorld slimeWorld = SWMPlugin.getInstance().loadWorld(loader, templateWorldName, true, worldData.toPropertyMap()).clone(worldName, loader);
                    Bukkit.getScheduler().runTask(SWMPlugin.getInstance(), () -> {
                        try {
                            SWMPlugin.getInstance().generateWorld(slimeWorld);
                        } catch (IllegalArgumentException ex) {
                            sender.sendMessage(Messages.prefixed("common.failed-generate", worldName, ex.getMessage()));
                            return;
                        }

                        // Register cloned world so /swm load works after unload
                        WorldData cloneData = worldData.copy();
                        cloneData.setDataSource(dataSource);
                        config.registerIfAbsent(worldName, cloneData);

                        sender.sendMessage(Messages.prefixed("common.world-loaded-ms", worldName, System.currentTimeMillis() - start));
                    });
                } catch (WorldAlreadyExistsException ex) {
                    sender.sendMessage(Messages.prefixed("clone.already-stored", worldName, dataSource));
                } catch (CorruptedWorldException ex) {
                    if (!(sender instanceof ConsoleCommandSender)) {
                        sender.sendMessage(Messages.prefixed("common.failed-load-corrupted", templateWorldName));
                    }

                    Logging.error("Failed to load world " + templateWorldName + ": world seems to be corrupted.");
                    ex.printStackTrace();
                } catch (NewerFormatException ex) {
                    sender.sendMessage(Messages.prefixed("common.failed-load-newer-format", templateWorldName, ex.getMessage()));
                } catch (UnknownWorldException ex) {
                    sender.sendMessage(Messages.prefixed("common.failed-load-unknown", templateWorldName, worldData.getDataSource()));
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(Messages.prefixed("common.failed-load-reason", templateWorldName, ex.getMessage()));
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender)) {
                        sender.sendMessage(Messages.prefixed("common.failed-load-io", templateWorldName));
                    }

                    Logging.error("Failed to load world " + templateWorldName + ":");
                    ex.printStackTrace();
                } catch (WorldInUseException ignored) {
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
        if (args.length == 4) {
            return new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
        }

        return Collections.emptyList();
    }
}
