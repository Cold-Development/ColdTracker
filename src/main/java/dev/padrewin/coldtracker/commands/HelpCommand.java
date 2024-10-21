package dev.padrewin.coldtracker.commands;

import java.util.Collections;
import java.util.List;
import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

public class HelpCommand extends BaseCommand {

    private final CommandHandler commandHandler;

    public HelpCommand(CommandHandler commandHandler) {
        super("help", CommandManager.CommandAliases.HELP);
        this.commandHandler = commandHandler;
    }

    @Override
    public void execute(ColdTracker plugin, CommandSender sender, String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        // Send header
        localeManager.sendMessage(sender, "command-help-title");

        // Send command descriptions the sender has permission for
        for (NamedExecutor executor : this.commandHandler.getExecutables())
            if (executor.hasPermission(sender))
                localeManager.sendSimpleMessage(sender, "command-" + executor.getName() + "-description");
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Permissible permissible) {
        return true;
    }

}