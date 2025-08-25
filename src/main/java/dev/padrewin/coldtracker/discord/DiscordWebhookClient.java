package dev.padrewin.coldtracker.discord;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class DiscordWebhookClient {

    private final String webhookUrl;

    public DiscordWebhookClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Sends a message with file content to Discord webhook as a downloadable file attachment
     *
     * @param message The message to send
     * @param fileName The name of the file attachment
     * @param fileContent The content of the file
     * @throws Exception if sending fails
     */
    public void sendMessageWithFile(String message, String fileName, String fileContent) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Create multipart boundary
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream os = connection.getOutputStream()) {
                // Write the JSON payload part
                String jsonPayload = "{\"content\": \"" + escapeJsonString(message) + "\"}";

                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.UTF_8));
                os.write("Content-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.write("\r\n".getBytes(StandardCharsets.UTF_8));

                // Write the file attachment part
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                os.write("Content-Type: text/yaml\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                os.write(fileContent.getBytes(StandardCharsets.UTF_8));
                os.write("\r\n".getBytes(StandardCharsets.UTF_8));

                // Write closing boundary
                os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                StringBuilder errorResponse = new StringBuilder();
                try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8)) {
                    while (scanner.hasNext()) {
                        errorResponse.append(scanner.nextLine());
                    }
                }
                throw new Exception("Discord webhook failed with code " + responseCode + ": " + errorResponse.toString());
            }

        } catch (Exception e) {
            if (connection != null && connection.getErrorStream() != null) {
                try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8)) {
                    StringBuilder errorDetails = new StringBuilder();
                    while (scanner.hasNext()) {
                        errorDetails.append(scanner.nextLine()).append("\n");
                    }
                    throw new Exception("Discord webhook error: " + e.getMessage() + " - Details: " + errorDetails.toString());
                }
            }
            throw e;
        }
    }

    /**
     * Helper method to properly escape strings for JSON
     */
    private String escapeJsonString(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

    /**
     * Sends a simple message to Discord webhook
     *
     * @param message The message to send
     * @throws Exception if sending fails
     */
    public void sendMessage(String message) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Properly escape the message for JSON
            String escapedMessage = message
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f");

            String json = "{\"content\": \"" + escapedMessage + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                throw new Exception("Discord webhook failed with response code: " + responseCode);
            }

        } catch (Exception e) {
            if (connection != null && connection.getErrorStream() != null) {
                try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8)) {
                    StringBuilder errorDetails = new StringBuilder();
                    while (scanner.hasNext()) {
                        errorDetails.append(scanner.nextLine()).append("\n");
                    }
                    throw new Exception("Discord webhook error: " + e.getMessage() + " - Details: " + errorDetails.toString());
                }
            }
            throw e;
        }
    }
}