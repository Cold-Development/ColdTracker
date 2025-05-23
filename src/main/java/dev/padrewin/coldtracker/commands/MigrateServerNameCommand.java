package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.util.ServerNameResolver;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MigrateServerNameCommand extends BaseCommand {

    public MigrateServerNameCommand() {
        super("migrate-server-name", CommandManager.CommandAliases.MIGRATE_SERVER_NAME);
    }

    @Override
    public void execute(ColdTracker plugin, CommandSender sender, String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);
        String prefix = localeManager.getLocaleMessage("prefix");

        if (args.length == 0) {
            // Show available server names
            Set<String> serverNames = ServerNameResolver.getAllServerNames();
            String currentName = ServerNameResolver.resolveServerName();

            sender.sendMessage(prefix + "&6Server Name Migration");
            sender.sendMessage(prefix + "&7Current server name: &a" + currentName);
            sender.sendMessage(prefix + "&7Available server names in database:");

            if (serverNames.isEmpty()) {
                sender.sendMessage(prefix + "&c  No server names found in database");
            } else {
                for (String name : serverNames) {
                    if (name.equals(currentName)) {
                        sender.sendMessage(prefix + "&a  • " + name + " &7(current)");
                    } else {
                        sender.sendMessage(prefix + "&7  • " + name);
                    }
                }
            }

            sender.sendMessage(prefix + localeManager.getLocaleMessage("command-migrate-server-name-usage"));
            return;
        }

        if (args.length != 2) {
            sender.sendMessage(prefix + localeManager.getLocaleMessage("invalid-command-usage"));
            sender.sendMessage(prefix + localeManager.getLocaleMessage("command-migrate-server-name-usage"));
            return;
        }

        String oldName = args[0];
        String newName = args[1];

        if (oldName.equals(newName)) {
            sender.sendMessage(prefix + localeManager.getLocaleMessage("command-migrate-server-name-same-error"));
            return;
        }

        // Confirm the migration
        sender.sendMessage(prefix + localeManager.getLocaleMessage("command-migrate-server-name-start")
                .replace("{old}", oldName)
                .replace("{new}", newName));

        plugin.getScheduler().runTaskAsync(() -> {
            int updatedRecords = ServerNameResolver.migrateServerName(oldName, newName);

            if (updatedRecords > 0) {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("command-migrate-server-name-success")
                        .replace("{records}", String.valueOf(updatedRecords))
                        .replace("{old}", oldName)
                        .replace("{new}", newName));
            } else {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("command-migrate-server-name-no-records")
                        .replace("{old}", oldName));
            }
        });
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Tab complete with existing server names
            Set<String> serverNames = ServerNameResolver.getAllServerNames();
            return new ArrayList<>(serverNames);
        } else if (args.length == 2) {
            // Tab complete with current server name as suggestion
            return Collections.singletonList(ServerNameResolver.resolveServerName());
        }
        return Collections.emptyList();
    }
}