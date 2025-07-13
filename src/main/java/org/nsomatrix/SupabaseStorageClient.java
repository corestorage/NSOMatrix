package org.nsomatrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SupabaseStorageClient {
    private final HttpClient client;
    private final ObjectMapper mapper;

    private String accessToken;
    private String userId;

    public SupabaseStorageClient() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        mapper = new ObjectMapper();
    }

    public void setAccessToken(String token) {
        accessToken = token;
        System.out.println("[StorageClient] Access Token set: " + (token == null ? "null" : "***"));
    }

    public void setUserId(String userId) {
        this.userId = userId;
        System.out.println("[StorageClient] User ID set: " + userId);
    }

    private void checkUserId() {
        if (userId == null || userId.isBlank())
            throw new IllegalStateException("User ID must be set before calling storage methods");
    }

    private String getPrefixedPath(String path) {
        checkUserId();
        if (path == null || path.isEmpty()) return userId + "/";
        if (path.startsWith("/")) path = path.substring(1);
        return userId + "/" + path;
    }

    private HttpRequest.Builder buildBaseRequest(URI uri, String method, HttpRequest.BodyPublisher body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("apikey", SupabaseClient.SUPABASE_ANON_KEY);

        if (accessToken != null && !accessToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + accessToken);
        } else {
            System.out.println("[StorageClient] Warning: no Authorization header (token missing)");
        }

        switch (method.toUpperCase()) {
            case "GET": builder.GET(); break;
            case "POST": builder.POST(body); break;
            case "PUT": builder.PUT(body); break;
            case "DELETE": builder.DELETE(); break;
            default: throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        return builder;
    }

    private void handleError(HttpResponse<?> response) throws IOException {
        if (response.statusCode() >= 400) {
            String body;
            try {
                if (response.body() instanceof String) {
                    body = (String) response.body();
                } else if (response.body() instanceof InputStream) {
                    InputStream is = (InputStream) response.body();
                    body = new String(is.readAllBytes());
                } else {
                    body = response.body().toString();
                }
            } catch (Exception e) {
                body = "Failed to read response body";
            }
            System.err.println("[StorageClient] API error " + response.statusCode() + ": " + body);
            throw new IOException("API request failed with status " + response.statusCode() + ". Body: " + body);
        }
    }

    public List<String> listFiles() throws IOException, InterruptedException {
        checkUserId();

        String prefix = userId.endsWith("/") ? userId : userId + "/";
        String url = SupabaseClient.SUPABASE_URL + "/storage/v1/object/list/" + SupabaseClient.STORAGE_BUCKET;
        String jsonBody = "{\"prefix\":\"" + prefix + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("apikey", SupabaseClient.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        handleError(response);

        List<String> files = new ArrayList<>();
        JsonNode root = mapper.readTree(response.body());

        if (root.isArray()) {
            for (JsonNode node : root) {
                String fullName = node.get("name").asText();
                if (fullName.startsWith(prefix)) {
                    files.add(fullName.substring(prefix.length()));
                } else {
                    files.add(fullName);
                }
            }
        }
        if (files.isEmpty()) files.add("<no files>");
        return files;
    }

    private static final long MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    public void uploadFile(File file) throws IOException, InterruptedException {
        checkUserId();

        if (file.length() > MAX_UPLOAD_SIZE_BYTES) {
            throw new IOException("File size exceeds the maximum allowed limit of 10MB.");
        }

        String path = getPrefixedPath(file.getName());
        URI uri = URI.create(SupabaseClient.SUPABASE_URL + "/storage/v1/object/" + SupabaseClient.STORAGE_BUCKET + "/" + path + "?upsert=true");

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofFile(file.toPath());

        HttpRequest.Builder builder = buildBaseRequest(uri, "POST", bodyPublisher);
        String mime = Files.probeContentType(file.toPath());
        builder.header("Content-Type", mime);

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        System.out.println("[StorageClient] UPLOAD response status: " + response.statusCode());
        System.out.println("[StorageClient] UPLOAD response body: " + response.body());

        handleError(response);
    }

    public void downloadFile(String remoteFileName, File destination) throws IOException, InterruptedException {
        checkUserId();

        String path = getPrefixedPath(remoteFileName);
        URI uri = URI.create(SupabaseClient.SUPABASE_URL + "/storage/v1/object/" + SupabaseClient.STORAGE_BUCKET + "/" + path);

        HttpRequest request = buildBaseRequest(uri, "GET", null).build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String body = new String(response.body().readAllBytes());
            throw new IOException("Download failed, status: " + response.statusCode() + ", body: " + body);
        }

        try (InputStream is = response.body(); OutputStream os = Files.newOutputStream(destination.toPath())) {
            is.transferTo(os);
        }
    }

    public void deleteFile(String remoteFileName) throws IOException, InterruptedException {
        checkUserId();

        String path = getPrefixedPath(remoteFileName);
        URI uri = URI.create(SupabaseClient.SUPABASE_URL + "/storage/v1/object/" + SupabaseClient.STORAGE_BUCKET + "/" + path);

        HttpRequest request = buildBaseRequest(uri, "DELETE", null).build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("[StorageClient] DELETE response status: " + response.statusCode());
        System.out.println("[StorageClient] DELETE response body: " + response.body());

        if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw new IOException("Delete failed, status: " + response.statusCode() + ", body: " + response.body());
        }
    }
}
