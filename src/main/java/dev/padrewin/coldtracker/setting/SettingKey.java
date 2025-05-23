package dev.padrewin.coldtracker.setting;

import dev.padrewin.colddev.config.CommentedConfigurationSection;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.config.ColdSettingSerializer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import dev.padrewin.coldtracker.ColdTracker;
import static dev.padrewin.colddev.config.ColdSettingSerializers.*;

public class SettingKey {

    private static final List<ColdSetting<?>> KEYS = new ArrayList<>();

    public static final ColdSetting<String> BASE_COMMAND_REDIRECT = create("base-command-redirect", STRING, "", "Which command should we redirect to when using '/coldtracker' with no subcommand specified?", "You can use a value here such as 'version' to show the output of '/coldtracker version'", "If you have any aliases defined, do not use them here", "If left as blank, the default behavior of showing '/coldtracker version' with bypassed permissions will be used");

    public static final ColdSetting<Boolean> DEBUG = create("debug", BOOLEAN, false, "Enable or disable debug logging for the plugin.", "If set to true, debug messages will be shown in the console.");

    public static final ColdSetting<Boolean> GIST_DUMP = create("gist-dump", BOOLEAN, false, "Enable or disable the Gist dump feature.", "This feature enables /coldtracker dump command", "Which will generate a Gist link with all the data collected from database.", "Note that you need a GitHub token to use this feature.");

    public static final ColdSetting<String> GIST_TOKEN = create("gist-token", STRING, "", "GitHub Personal Access Token for creating Gists.", "Please check the following link to see how to get your Gist token.", "Wiki: https://github.com/Cold-Development/ColdTracker/wiki/Gist-Token");

    public static final ColdSetting<List<String>> GIST_HEADER = create("gist-header", STRING_LIST,
            Arrays.asList(
                    "##############################",
                    "# MC-1ST.RO | STAFF ACTIVITY #",
                    "#    Developer @ padrewin    #",
                    "##############################"
            ),
            "The customizable header for the Gist dump file.",
            "Each line represents a separate string in the header.",
            "If you don't want a header, please leave an empty line",
            "Example:",
            "gist-header:",
            "- ''"
    );

    public static final ColdSetting<String> FILE_PREFIX = create("file-prefix", STRING, "staff_activity_",
            "The prefix for the exported gist / file.", "For example, you could set 'survival_' or 'boxpvp_' etc.");

    public static final ColdSetting<Boolean> TRACK_VOTES = create("track-votes", BOOLEAN, false,
            "Enable or disable tracking of player votes.",
            "This feature requires NuVotifier plugin to be installed.",
            "Plugin: https://www.spigotmc.org/resources/nuvotifier.13449/",
            "If set to true, the plugin will track votes and include them in exports/dumps.",
            "If set to false, votes will be ignored.");

    public static final ColdSetting<String> FOLDER_NAME = create("folder-name", STRING, "exported-database",
            "The name of the folder where exported files will be saved.",
            "If left blank, it will default to 'exported database'.");

    // SERVER CONFIGURATION
    public static final ColdSetting<String> SERVER_NAME = create("server.name", STRING, "auto",
            "The display name for this server.",
            "This will be used in stats displays and exports.",
            "Examples: 'Survival', 'Creative', 'Skyblock', 'Hub', etc.",
            "If set to 'auto', it will try to detect the server name automatically.");

    public static final ColdSetting<String> SERVER_NAME_DETECTION = create("server.name-detection", STRING, "folder",
            "How to automatically detect the server name when SERVER_NAME is set to 'auto':",
            "- 'world': Use the first world's name",
            "- 'jar': Use the jar file name",
            "- 'folder': Use the server folder name",
            "- 'config': Use the value from SERVER_NAME setting (ignores auto detection)");

    // STORAGE OPTIONS
    public static final ColdSetting<String> STORAGE_TYPE = create("storage.type", STRING, "sqlite", "The type of storage to use: 'sqlite' or 'mysql'.");

    public static final ColdSetting<String> MYSQL_HOST = create("storage.mysql.host", STRING, "localhost", "MySQL host.");
    public static final ColdSetting<Integer> MYSQL_PORT = create("storage.mysql.port", INTEGER, 3306, "MySQL port.");
    public static final ColdSetting<String> MYSQL_DATABASE = create("storage.mysql.database", STRING, "coldtracker", "MySQL database name.");
    public static final ColdSetting<String> MYSQL_USERNAME = create("storage.mysql.username", STRING, "root", "MySQL username.");
    public static final ColdSetting<String> MYSQL_PASSWORD = create("storage.mysql.password", STRING, "password", "MySQL password.");

    // ROTATION OPTIONS (Updated)
    public static final ColdSetting<Boolean> ROTATION_ENABLED = create("rotation.enabled", BOOLEAN, false, "Enable automatic monthly or weekly data rotation/export.");
    public static final ColdSetting<String> ROTATION_FREQUENCY = create("rotation.frequency", STRING, "monthly", "Can be 'monthly' or 'weekly'.");
    public static final ColdSetting<Integer> ROTATION_START_DAY = create("rotation.start-day", INTEGER, 1,
            "Day to start the rotation.",
            "For monthly: 1-31 (day of month)",
            "For weekly: 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday",
            "Examples: start-day: 1 (monthly = 1st of month, weekly = Monday)",
            "          start-day: 5 (monthly = 5th of month, weekly = Friday)");
    public static final ColdSetting<Boolean> ROTATION_CLEAN_AFTER = create("rotation.clean-after-export", BOOLEAN, true, "If true, the database is cleared after each export.");
    public static final ColdSetting<Boolean> ROTATION_SEND_TO_DISCORD = create("rotation.send-to-discord", BOOLEAN, false, "If true, rotation exports are automatically sent to Discord webhook.");
    public static final ColdSetting<Boolean> ROTATION_SAVE_LOCAL = create("rotation.save-local", BOOLEAN, true, "If true, rotation exports are saved locally in the export folder.");

    // DISCORD WEBHOOK
    public static final ColdSetting<String> DISCORD_WEBHOOK = create("discord-webhook", STRING, "", "Discord webhook URL to send export files to.");
    public static final ColdSetting<Boolean> SEND_EXPORT_TO_DISCORD = create("send-export-to-discord", BOOLEAN, false, "Whether to send exports to Discord via webhook.");

    private static <T> ColdSetting<T> create(String key, ColdSettingSerializer<T> serializer, T defaultValue, String... comments) {
        ColdSetting<T> setting = ColdSetting.backed(ColdTracker.getInstance(), key, serializer, defaultValue, comments);
        KEYS.add(setting);
        return setting;
    }

    private static ColdSetting<CommentedConfigurationSection> create(String key, String... comments) {
        ColdSetting<CommentedConfigurationSection> setting = ColdSetting.backedSection(ColdTracker.getInstance(), key, comments);
        KEYS.add(setting);
        return setting;
    }

    public static List<ColdSetting<?>> getKeys() {
        return Collections.unmodifiableList(KEYS);
    }

    private SettingKey() {}
}