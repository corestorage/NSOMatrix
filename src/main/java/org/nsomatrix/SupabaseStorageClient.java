package org.nsomatrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SupabaseStorageClient {
    private final ObjectMapper mapper;

    private String accessToken;
    private String userId;

    public SupabaseStorageClient() {
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
        if (userId == null || userId.trim().isEmpty())
            throw new IllegalStateException("User ID must be set before calling storage methods");
    }

    private String getPrefixedPath(String path) {
        checkUserId();
        if (path == null || path.isEmpty()) return userId + "/";
        if (path.startsWith("/")) path = path.substring(1);
        return userId + "/" + path;
    }

    private HttpURLConnection buildBaseRequest(URL url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("apikey", SupabaseClient.SUPABASE_ANON_KEY);

        if (accessToken != null && !accessToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        } else {
            System.out.println("[StorageClient] Warning: no Authorization header (token missing)");
        }

        return conn;
    }

    private void handleError(HttpURLConnection conn) throws IOException {
        int statusCode = conn.getResponseCode();
        if (statusCode >= 400) {
            InputStream errorStream = conn.getErrorStream();
            String body = "Failed to read error stream";
            if (errorStream != null) {
                try (InputStreamReader isr = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                     BufferedReader br = new BufferedReader(isr)) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    body = sb.toString();
                } catch (Exception e) {
                    // Ignore
                }
            }
            System.err.println("[StorageClient] API error " + statusCode + ": " + body);
            throw new IOException("API request failed with status " + statusCode + ". Body: " + body);
        }
    }

    public List<String> listFiles() throws IOException, InterruptedException {
        checkUserId();

        String urlStr = SupabaseClient.SUPABASE_URL + "/storage/v1/object/list/" + SupabaseClient.STORAGE_BUCKET;
        String prefix = userId.endsWith("/") ? userId : userId + "/";
        String jsonBody = "{\"prefix\":\"" + prefix + "\"}";

        HttpURLConnection conn = buildBaseRequest(new URL(urlStr), "POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        handleError(conn);

        List<String> files = new ArrayList<>();
        try (InputStream is = conn.getInputStream()) {
            JsonNode root = mapper.readTree(is);
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
        URL url = new URL(SupabaseClient.SUPABASE_URL + "/storage/v1/object/" + SupabaseClient.STORAGE_BUCKET + "/" + path + "?upsert=true");

        HttpURLConnection conn = buildBaseRequest(url, "POST");
        String mime = Files.probeContentType(file.toPath());
        conn.setRequestProperty("Content-Type", mime);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream(); FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        System.out.println("[StorageClient] UPLOAD response status: " + conn.getResponseCode());

        handleError(conn);
    }

    public void downloadFile(String remoteFileName, File destination) throws IOException, InterruptedException {
        checkUserId();

        String path = getPrefixedPath(remoteFileName);
        URL url = new URL(SupabaseClient.SUPABASE_URL + "/storage/v1/object/" + SupabaseClient.STORAGE_BUCKET + "/" + path);

        HttpURLConnection conn = buildBaseRequest(url, "GET");

        if (conn.getResponseCode() != 200) {
            handleError(conn);
        }

        try (InputStream is = conn.getInputStream(); OutputStream os = Files.newOutputStream(destination.toPath())) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    public void deleteFile(String remoteFileName) throws IOException, InterruptedException {
        checkUserId();

        String path = getPrefixedPath(remoteFileName);
        URL url = new URL(SupabaseClient.SUPABASE_URL + "/storage/v1/object/" + SupabaseClient.STORAGE_BUCKET + "/" + path);

        HttpURLConnection conn = buildBaseRequest(url, "DELETE");

        System.out.println("[StorageClient] DELETE response status: " + conn.getResponseCode());

        if (conn.getResponseCode() != 204 && conn.getResponseCode() != 200) {
            handleError(conn);
        }
    }
}
