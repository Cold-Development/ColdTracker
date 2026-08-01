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

    public static final ColdSetting<Boolean> TRACK_SANCTIONS = create("track-sanctions", BOOLEAN, false,
            "Enable or disable tracking of staff sanctions (mutes, bans, kicks, warnings).",
            "This feature requires the LiteBans plugin to be installed.",
            "Plugin: https://www.spigotmc.org/resources/litebans.3715/",
            "If set to true, the plugin will read sanction counts from LiteBans and include them in stats/exports/dumps.",
            "If set to false, sanctions will be ignored.");

    public static final ColdSetting<String> FOLDER_NAME = create("folder-name", STRING, "exported-database",
            "The name of the folder where exported files will be saved.",
            "If left blank, it will default to 'exported database'.");

    // Flexible Scheduling Settings
    public static final ColdSetting<Boolean> SCHEDULED_REPORTS_ENABLED = create("scheduled-reports.enabled", BOOLEAN, false,
            "Enable or disable scheduled staff activity reports sent to Discord.",
            "When enabled, the plugin will automatically generate and send reports based on your schedule configuration.",
            "Perfect for automated staff reviews and performance tracking.",
            "Requires a valid Discord webhook URL to be configured.");

    public static final ColdSetting<String> DISCORD_WEBHOOK_URL = create("scheduled-reports.discord-webhook-url", STRING, "",
            "Discord webhook URL for sending scheduled reports.",
            "To get a webhook URL:",
            "1. Go to your Discord server settings",
            "2. Navigate to Integrations > Webhooks",
            "3. Click 'Create Webhook' or 'New Webhook'",
            "4. Copy the webhook URL",
            "Example: https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz123456789");

    public static final ColdSetting<String> DISCORD_MESSAGE = create("scheduled-reports.discord-message", STRING, "📊 **{schedule_type} Staff Activity Report**\n\nHere's the staff activity summary!\n\n*Generated automatically on {date} at {time}*",
            "The message that will be sent along with the scheduled staff activity report.",
            "This message appears above the embedded report data in Discord.",
            "Available placeholders:",
            "- {schedule_type} - The schedule type (Daily, Weekly, Monthly, Interval)",
            "- {date} - Current date when report is sent (MM/dd/yyyy)",
            "- {time} - Current time when report is sent (HH:mm)",
            "- {month} - Month name (e.g., JANUARY, FEBRUARY)",
            "- {year} - Year (e.g., 2024)",
            "- {day} - Day of month (e.g., 15)",
            "- {weekday} - Day of week (e.g., MONDAY, TUESDAY)",
            "You can use Discord markdown formatting here.");

    public static final ColdSetting<String> SCHEDULE_TYPE = create("scheduled-reports.schedule-type", STRING, "monthly",
            "The type of schedule for sending reports.",
            "Valid options:",
            "- 'daily' - Send report once per day at a specific time",
            "- 'weekly' - Send report once per week on a specific day and time",
            "- 'monthly' - Send report once per month on a specific day and time",
            "- 'interval' - Send report every X hours");

    // Daily Schedule Settings
    public static final ColdSetting<String> DAILY_TIME = create("scheduled-reports.daily.time", STRING, "00:00",
            "Time of day to send daily reports (24-hour format: HH:mm).",
            "Only used when schedule-type is set to 'daily'.",
            "Examples: '00:00' for midnight, '14:30' for 2:30 PM, '23:59' for 11:59 PM");

    // Weekly Schedule Settings
    public static final ColdSetting<String> WEEKLY_DAY = create("scheduled-reports.weekly.day", STRING, "SUNDAY",
            "Day of the week to send weekly reports.",
            "Only used when schedule-type is set to 'weekly'.",
            "Valid options: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY");

    public static final ColdSetting<String> WEEKLY_TIME = create("scheduled-reports.weekly.time", STRING, "00:00",
            "Time of day to send weekly reports (24-hour format: HH:mm).",
            "Only used when schedule-type is set to 'weekly'.",
            "Examples: '00:00' for midnight, '14:30' for 2:30 PM, '23:59' for 11:59 PM");

    // Monthly Schedule Settings
    public static final ColdSetting<Integer> MONTHLY_DAY = create("scheduled-reports.monthly.day-of-month", INTEGER, 1,
            "Day of the month to send the monthly report (1-31).",
            "Only used when schedule-type is set to 'monthly'.",
            "Set to 1 to send on the 1st of each month (recommended).",
            "For months with fewer days (e.g., February), the report will be sent on the last day of that month.",
            "Examples: 1 = 1st of month, 15 = 15th of month, 31 = last day of month");

    public static final ColdSetting<String> MONTHLY_TIME = create("scheduled-reports.monthly.time", STRING, "00:00",
            "Time of day to send monthly reports (24-hour format: HH:mm).",
            "Only used when schedule-type is set to 'monthly'.",
            "Examples: '00:00' for midnight, '12:00' for noon, '14:30' for 2:30 PM, '23:59' for 11:59 PM");

    // Interval Schedule Settings
    public static final ColdSetting<Integer> INTERVAL_MINUTES = create("scheduled-reports.interval.minutes", INTEGER, 60,
            "Number of minutes between interval-based reports.",
            "Only used when schedule-type is set to 'interval'.",
            "Minimum value: 1 minute, Maximum recommended: 10080 minutes (1 week)",
            "Examples: 1 = every minute, 5 = every 5 minutes, 60 = hourly, 1440 = daily, 10080 = weekly",
            "Perfect for testing: Set to 1 or 2 minutes to test functionality");

    // General Schedule Settings
    public static final ColdSetting<Boolean> AUTO_WIPE_AFTER_SEND = create("scheduled-reports.auto-wipe-after-send", BOOLEAN, false,
            "Whether to automatically wipe the database after sending a scheduled report.",
            "⚠️  WARNING: This will permanently delete all tracked staff time and vote data!",
            "Recommended settings by schedule type:",
            "- Daily: false (keep data for longer-term analysis)",
            "- Weekly: false (keep data for monthly reports)",
            "- Monthly: true (start fresh each month)",
            "- Interval: depends on your needs",
            "Note: Manual exports and dumps will NOT trigger auto-wipe.");

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
