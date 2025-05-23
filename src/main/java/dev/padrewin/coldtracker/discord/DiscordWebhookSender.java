package dev.padrewin.coldtracker.discord;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.setting.SettingKey;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class DiscordWebhookSender {

    public static void sendFileToWebhook(String webhookUrl, File file, ColdTracker plugin) {
        if (webhookUrl == null || webhookUrl.isBlank() || !file.exists()) {
            if (SettingKey.DEBUG.get()) {
                plugin.getLogger().warning("[DEBUG] Webhook URL is empty or file does not exist.");
            }
            return;
        }

        try {
            String boundary = Long.toHexString(System.currentTimeMillis());
            String CRLF = "\r\n";

            HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream output = connection.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)) {

                // Add file
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"").append(CRLF);
                writer.append("Content-Type: " + Files.probeContentType(file.toPath())).append(CRLF);
                writer.append(CRLF).flush();
                Files.copy(file.toPath(), output);
                output.flush();
                writer.append(CRLF).flush();

                // End of multipart/form-data.
                writer.append("--" + boundary + "--").append(CRLF).flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 204 || responseCode == 200) {
                if (SettingKey.DEBUG.get()) {
                    plugin.getLogger().info("[DEBUG] Export file successfully sent to Discord webhook.");
                }
            } else {
                plugin.getLogger().severe("Failed to send file to Discord webhook. Response code: " + responseCode);
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Error sending file to Discord webhook: " + e.getMessage());
            if (SettingKey.DEBUG.get()) {
                e.printStackTrace();
            }
        }
    }
}
