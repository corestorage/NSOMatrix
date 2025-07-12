package org.nsomatrix;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SupabaseStorageClient - handles file operations in a Supabase storage bucket,
 * isolating files per user by prefixing paths with user UUID,
 * using user JWT for authenticated requests.
 */
public class SupabaseStorageClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;  // User JWT token
    private String userId;       // User UUID (used as folder prefix)

    /**
     * Constructor initializes HttpClient and ObjectMapper.
     */
    public SupabaseStorageClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sets the user JWT token for authenticated requests.
     * @param token JWT access token obtained upon user login
     */
    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    /**
     * Sets the current user's UUID used for prefixing file paths.
     * @param userId UUID string representing the current user
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Prepends the userId folder prefix to a given file path.
     * @param path File path relative to the user folder (can be empty)
     * @return Fully prefixed file path in storage bucket
     */
    private String prefixPath(String path) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("User ID must be set before calling storage methods");
        }
        if (path == null || path.isBlank()) {
            return userId + "/";
        }
        return userId + "/" + path;
    }

    /**
     * Lists files in the logged-in user's folder.
     * @return List of file names (without userId prefix)
     * @throws IOException on network issues or bad response
     * @throws InterruptedException if request is interrupted
     */
    public List<String> listFiles() throws IOException, InterruptedException {
        String prefix = prefixPath("");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/list/" + SupabaseConfig.STORAGE_BUCKET + "?prefix=" + prefix))
                .GET()
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        // Debug output
        System.out.println("[SupabaseStorageClient] List files request URL: " + request.uri());
        System.out.println("[SupabaseStorageClient] Bucket: " + SupabaseConfig.STORAGE_BUCKET);
        System.out.println("[SupabaseStorageClient] Access token present: " + (accessToken != null && !accessToken.isBlank()));

        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to list files, status: " + response.statusCode() + ", body: " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        List<String> files = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                String fullName = item.get("name").asText();
                files.add(fullName.substring(prefix.length())); // Remove userId prefix
            }
        }
        return files;
    }

    /**
     * Uploads a file to the user's folder in the storage bucket.
     * @param file Local file to upload
     * @throws IOException on network or file IO errors
     * @throws InterruptedException if request is interrupted
     */
    public void uploadFile(File file) throws IOException, InterruptedException {
        String path = prefixPath(file.getName());

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofFile(file.toPath());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/" + SupabaseConfig.STORAGE_BUCKET + "/" + path))
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", Files.probeContentType(file.toPath()))
                .PUT(bodyPublisher)
                .build();

        System.out.println("[SupabaseStorageClient] Upload file request URL: " + request.uri());
        System.out.println("[SupabaseStorageClient] Uploading file: " + file.getAbsolutePath());

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to upload file, status: " + response.statusCode() + ", body: " + response.body());
        }
    }

    /**
     * Downloads a file from the user's folder in the storage bucket.
     * @param remoteFileName Filename in storage (without userId prefix)
     * @param localFile Local file to save the data
     * @throws IOException on network or file IO errors
     * @throws InterruptedException if request is interrupted
     */
    public void downloadFile(String remoteFileName, File localFile) throws IOException, InterruptedException {
        String path = prefixPath(remoteFileName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/" + SupabaseConfig.STORAGE_BUCKET + "/" + path))
                .GET()
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        System.out.println("[SupabaseStorageClient] Download file request URL: " + request.uri());
        System.out.println("[SupabaseStorageClient] Downloading to: " + localFile.getAbsolutePath());

        HttpResponse<InputStream> response = httpClient.send(request, BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String body = new String(response.body().readAllBytes());
            throw new IOException("Failed to download file, status: " + response.statusCode() + ", body: " + body);
        }

        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(localFile.toPath())) {
            is.transferTo(os);
        }
    }

    /**
     * Deletes a file from the user's folder in the storage bucket.
     * @param remoteFileName Filename in storage (without userId prefix)
     * @throws IOException on network errors
     * @throws InterruptedException if request is interrupted
     */
    public void deleteFile(String remoteFileName) throws IOException, InterruptedException {
        String path = prefixPath(remoteFileName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/" + SupabaseConfig.STORAGE_BUCKET + "/" + path))
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();

        System.out.println("[SupabaseStorageClient] Delete file request URL: " + request.uri());
        System.out.println("[SupabaseStorageClient] Deleting file: " + remoteFileName);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("Failed to delete file, status: " + response.statusCode() + ", body: " + response.body());
        }
    }
}