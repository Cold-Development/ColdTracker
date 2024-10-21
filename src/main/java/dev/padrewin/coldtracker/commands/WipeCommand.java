package dev.padrewin.coldtracker.commands;

import java.util.Collections;
import java.util.List;
import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class WipeCommand extends BaseCommand {

    public WipeCommand() {
        super("wipe", CommandManager.CommandAliases.WIPE);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        // Verifică permisiunea
        if (!sender.hasPermission("coldtracker.wipe")) {
            localeManager.sendMessage(sender, "no-permission");
            return;
        }

        // Verifică dacă argumentul "confirm" este furnizat
        if (args.length < 1 || !args[0].equalsIgnoreCase("confirm")) {
            localeManager.sendMessage(sender, "command-wipe-warning");
            return;
        }

        // Execută comanda de wipe
        plugin.getDatabaseManager().wipeDatabaseTables();
        localeManager.sendMessage(sender, "command-wipe-success");
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

}