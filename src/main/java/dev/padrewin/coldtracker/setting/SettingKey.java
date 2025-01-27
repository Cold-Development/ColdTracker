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

    public static final ColdSetting<String> BASE_COMMAND_REDIRECT = create("base-command-redirect", STRING, "", "Which command should we redirect to when using '/coldtracker' with no subcommand specified?", "You can use a value here such as 'version' to show the output of '/coldtracker version'", "If you have any aliases defined, do not use them here", "If left as blank, the default behavior of showing '/giveall version' with bypassed permissions will be used");

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
