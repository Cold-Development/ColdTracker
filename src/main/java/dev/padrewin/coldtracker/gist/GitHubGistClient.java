package dev.padrewin.coldtracker.gist;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class GitHubGistClient {

    private final String accessToken;

    public GitHubGistClient(String accessToken) {
        this.accessToken = accessToken;
    }

    public String createGist(String content, String filename, boolean isPublic) throws Exception {
        HttpURLConnection connection = null;
        try {
            String apiUrl = "https://api.github.com/gists";

            String escapedContent = content.replace("\"", "\\\"").replace("\n", "\\n");

            String json = "{"
                    + "\"description\": \"Gist created from ColdTracker plugin\","
                    + "\"public\": " + isPublic + ","
                    + "\"files\": {"
                    + "\"" + filename + "\": {"
                    + "\"content\": \"" + escapedContent + "\""
                    + "}"
                    + "}"
                    + "}";

            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "token " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            StringBuilder response = new StringBuilder();
            try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8)) {
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
            }

            if (connection.getResponseCode() == 201) {
                return response.toString().split("\"html_url\":\"")[1].split("\"")[0];
            } else {
                throw new Exception("Failed to create Gist: " + connection.getResponseCode());
            }

        } catch (Exception e) {
            if (connection != null) {
                try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8)) {
                    while (scanner.hasNext()) {
                        System.err.println("GitHub Gist API Error: " + scanner.nextLine());
                    }
                }
            }
            throw e;
        }
    }
}