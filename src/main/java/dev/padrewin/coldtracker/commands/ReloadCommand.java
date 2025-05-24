package dev.padrewin.coldtracker.commands;

import java.util.Collections;
import java.util.List;
import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends BaseCommand {

    public ReloadCommand() {
        super("reload", CommandManager.CommandAliases.RELOAD);
    }

    @Override
    public void execute(ColdTracker plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("coldtracker.reload")) {
            plugin.getManager(LocaleManager.class).sendMessage(sender, "no-permission");
            return;
        }

        if (args.length > 0) {
            plugin.getManager(LocaleManager.class).sendMessage(sender, "command-reload-usage");
            return;
        }

        plugin.reloadConfig();
        plugin.reload();
        plugin.getManager(LocaleManager.class).sendMessage(sender, "command-reload-success");
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
