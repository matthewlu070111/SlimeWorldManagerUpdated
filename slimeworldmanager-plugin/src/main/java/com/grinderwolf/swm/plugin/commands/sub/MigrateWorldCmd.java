package com.grinderwolf.swm.plugin.commands.sub;

import com.grinderwolf.swm.api.exceptions.UnknownWorldException;
import com.grinderwolf.swm.api.exceptions.WorldAlreadyExistsException;
import com.grinderwolf.swm.api.exceptions.WorldInUseException;
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MigrateWorldCmd implements Subcommand {

    @Override
    public String getUsage() {
        return "migrate <world> <new-data-source>";
    }

    @Override
    public String getDescription() {
        return Messages.get("cmd.migrate.description");
    }

    @Override
    public String getPermission() {
        return "swm.migrate";
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String worldName = args[0];
            WorldsConfig config = ConfigManager.getWorldConfig();
            WorldData worldData = config.getWorlds().get(worldName);

            if (worldData == null) {
                sender.sendMessage(Messages.prefixed("migrate.unknown-world", worldName));
                return true;
            }

            String newSource = args[1];
            SlimeLoader newLoader = LoaderUtils.getLoader(newSource);

            if (newLoader == null) {
                sender.sendMessage(Messages.prefixed("common.unknown-data-source", newSource));
                return true;
            }

            String currentSource = worldData.getDataSource();

            if (newSource.equalsIgnoreCase(currentSource)) {
                sender.sendMessage(Messages.prefixed("migrate.same-source", worldName, currentSource));
                return true;
            }

            SlimeLoader oldLoader = LoaderUtils.getLoader(currentSource);

            if (oldLoader == null) {
                sender.sendMessage(Messages.prefixed("migrate.unknown-current-source", currentSource));
                return true;
            }

            if (CommandManager.getInstance().getWorldsInUse().contains(worldName)) {
                sender.sendMessage(Messages.prefixed("common.world-in-use", worldName));
                return true;
            }

            CommandManager.getInstance().getWorldsInUse().add(worldName);

            Bukkit.getScheduler().runTaskAsynchronously(SWMPlugin.getInstance(), () -> {

                try {
                    long start = System.currentTimeMillis();
                    SWMPlugin.getInstance().migrateWorld(worldName, oldLoader, newLoader);

                    worldData.setDataSource(newSource);
                    config.save();

                    sender.sendMessage(Messages.prefixed("migrate.success", worldName, System.currentTimeMillis() - start));
                } catch (IOException ex) {
                    if (!(sender instanceof ConsoleCommandSender)) {
                        sender.sendMessage(Messages.prefixed("migrate.failed-io", worldName, currentSource, newSource));
                    }

                    Logging.error("Failed to load world " + worldName + " (using data source " + currentSource + "):");
                    ex.printStackTrace();
                } catch (WorldInUseException ex) {
                    sender.sendMessage(Messages.prefixed("migrate.in-use", worldName));
                } catch (WorldAlreadyExistsException ex) {
                    sender.sendMessage(Messages.prefixed("migrate.target-has-world", newSource, worldName));
                } catch (UnknownWorldException ex) {
                    sender.sendMessage(Messages.prefixed("migrate.not-found", worldName, currentSource));
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

        if (args.length == 3) {
            toReturn = new LinkedList<>(LoaderUtils.getAvailableLoadersNames());
        }

        return toReturn == null ? Collections.emptyList() : toReturn;
    }
}
