package dev.padrewin.coldtracker.setting;

import dev.padrewin.colddev.config.CommentedConfigurationSection;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.config.ColdSettingSerializer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dev.padrewin.coldtracker.ColdTracker;
import static dev.padrewin.colddev.config.ColdSettingSerializers.*;

public class SettingKey {

    private static final List<ColdSetting<?>> KEYS = new ArrayList<>();

    public static final ColdSetting<String> BASE_COMMAND_REDIRECT = create("base-command-redirect", STRING, "", "Which command should we redirect to when using '/giveall' with no subcommand specified?", "You can use a value here such as 'version' to show the output of '/giveall version'", "If you have any aliases defined, do not use them here", "If left as blank, the default behavior of showing '/giveall version' with bypassed permissions will be used");

    public static final ColdSetting<Boolean> DEBUG = create("debug", BOOLEAN, false, "Enable or disable debug logging for the plugin.", "If set to true, debug messages will be shown in the console.");

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