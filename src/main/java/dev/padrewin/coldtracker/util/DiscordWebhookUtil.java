package dev.padrewin.coldtracker.util;

import dev.padrewin.coldtracker.ColdTracker;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class DiscordWebhookUtil {

    public static boolean sendFileToDiscord(String webhookUrl, File file) {
        if (webhookUrl == null || webhookUrl.isBlank() || !file.exists()) {
            return false;
        }

        ColdTracker plugin = ColdTracker.getInstance();
        boolean debug = plugin.getConfig().getBoolean("debug", false);

        String boundary = "===" + System.currentTimeMillis() + "===";
        String lineSeparator = "\r\n";

        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "ColdTracker-Discord-Bot/1.0");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(30000);    // 30 seconds

            if (debug) {
                plugin.getLogger().info("[DEBUG] Sending file to Discord: " + file.getName());
            }

            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                // File part
                out.writeBytes("--" + boundary + lineSeparator);
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + lineSeparator);
                out.writeBytes("Content-Type: text/plain" + lineSeparator + lineSeparator);
                Files.copy(file.toPath(), out);
                out.writeBytes(lineSeparator);

                // Finish multipart
                out.writeBytes("--" + boundary + "--" + lineSeparator);
                out.flush();
            }

            int responseCode = connection.getResponseCode();

            // Log response for debugging
            if (responseCode >= 200 && responseCode < 300) {
                if (debug) {
                    plugin.getLogger().info("[DEBUG] File sent successfully to Discord. Response code: " + responseCode);
                }
                return true;
            } else {
                plugin.getLogger().severe("[DiscordWebhookUtil] Failed to send file to Discord. Response code: " + responseCode);

                // Try to read error response
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                    plugin.getLogger().severe("[DiscordWebhookUtil] Discord API Response: " + response.toString());
                } catch (Exception e) {
                    if (debug) {
                        plugin.getLogger().warning("[DEBUG] Could not read Discord error response: " + e.getMessage());
                    }
                }
                return false;
            }

        } catch (IOException e) {
            plugin.getLogger().severe("[DiscordWebhookUtil] Error sending file to Discord: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }
}