package org.nsomatrix;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class SupabaseAuthService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SupabaseAuthService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseClient.SUPABASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("apikey", SupabaseClient.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseClient.SUPABASE_ANON_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
            return Optional.of(authResponse);
        } else {
            System.err.println("Failed auth request, status: " + response.statusCode() + ", body: " + response.body());
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
