package org.nsomatrix;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class SupabaseAuthService {

    private final ObjectMapper objectMapper;

    public SupabaseAuthService() {
        objectMapper = new ObjectMapper();
    }

    public Optional<AuthResponse> signUp(String email, String password) throws IOException, InterruptedException {
        return authRequest("/auth/v1/signup", email, password);
    }

    public Optional<AuthResponse> signIn(String email, String password) throws IOException, InterruptedException {
        return authRequest("/auth/v1/token?grant_type=password", email, password);
    }

    private Optional<AuthResponse> authRequest(String endpoint, String email, String password) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email);
        body.put("password", password);

        URL url = new URL(SupabaseClient.SUPABASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", SupabaseClient.SUPABASE_ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.SUPABASE_ANON_KEY);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();

        if (responseCode == 200 || responseCode == 201) {
            try (InputStream is = conn.getInputStream()) {
                AuthResponse authResponse = objectMapper.readValue(is, AuthResponse.class);
                return Optional.of(authResponse);
            }
        } else {
            System.err.println("Failed auth request, status: " + responseCode);
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthResponse {
        public String access_token;
        public String refresh_token;
        public User user;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class User {
            public String id;
            public String email;
            public String confirmed_at;
        }
    }
}
