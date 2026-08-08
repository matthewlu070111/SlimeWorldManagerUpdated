package com.grinderwolf.swm.plugin.commands;

import com.grinderwolf.swm.plugin.commands.sub.*;
import com.grinderwolf.swm.plugin.locale.Messages;
import lombok.Getter;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandManager implements TabExecutor {

    @Getter
    private static CommandManager instance;
    private Map<String, Subcommand> commands = new HashMap<>();

    /* A list containing all the worlds that are being performed operations on, so two commands cannot be run at the same time */
    @Getter
    private final Set<String> worldsInUse = new HashSet<>();

    public CommandManager() {
        instance = this;

        commands.put("help", new HelpCmd());
        commands.put("version", new VersionCmd());
        commands.put("goto", new GotoCmd());
        commands.put("load", new LoadWorldCmd());
        commands.put("load-template", new LoadTemplateWorldCmd());
        commands.put("clone-world", new CloneWorldCmd());
        commands.put("unload", new UnloadWorldCmd());
        commands.put("list", new WorldListCmd());
        commands.put("dslist", new DSListCmd());
        commands.put("migrate", new MigrateWorldCmd());
        commands.put("delete", new DeleteWorldCmd());
        commands.put("import", new ImportWorldCmd());
        commands.put("reload", new ReloadConfigCmd());
        commands.put("create", new CreateWorldCmd());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Messages.prefixed("command.main-info"));
            return true;
        }

        Subcommand command = commands.get(args[0]);

        if (command == null) {
            sender.sendMessage(Messages.prefixed("command.unknown"));
            return true;
        }

        if (command.inGameOnly() && !(sender instanceof Player)) {
            sender.sendMessage(Messages.prefixed("command.in-game-only"));
            return true;
        }

        if (!command.getPermission().equals("") && !sender.hasPermission(command.getPermission()) && !sender.hasPermission("swm.*")) {
            sender.sendMessage(Messages.prefixed("command.no-permission"));
            return true;
        }

        String[] subCmdArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCmdArgs, 0, subCmdArgs.length);

        if (!command.onCommand(sender, subCmdArgs)) {
            sender.sendMessage(Messages.prefixed("command.usage", command.getUsage()));
        }

        return true;
    }

    public Collection<Subcommand> getCommands() {
        return commands.values();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> toReturn = null;
        final String typed = args[0].toLowerCase();

        if (args.length == 1) {
            for (Map.Entry<String, Subcommand> entry : commands.entrySet()) {
                final String name = entry.getKey();
                final Subcommand subcommand = entry.getValue();

                if (name.startsWith(typed) && !subcommand.getPermission().equals("")
                        && (sender.hasPermission(subcommand.getPermission()) || sender.hasPermission("swm.*"))) {

                    if (name.equalsIgnoreCase("goto") && (sender instanceof ConsoleCommandSender)) {
                        continue;
                    }

                    if (toReturn == null) {
                        toReturn = new LinkedList<>();
                    }

                    toReturn.add(name);
                }
            }
        }

        if (args.length > 1) {
            final String subName = args[0];
            final Subcommand subcommand = commands.get(subName);

            if (subcommand != null) {
                toReturn = subcommand.onTabComplete(sender, args);
            }
        }

        return toReturn == null ? Collections.emptyList() : toReturn;
    }
}
