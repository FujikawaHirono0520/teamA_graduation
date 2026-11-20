package com.example.teama_management.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class FirebaseAuthRestClient {

    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={key}";

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public FirebaseAuthRestClient(@Value("${firebase.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public record SignInResult(String uid, String displayName, String email) {}

    public static class AuthFailedException extends RuntimeException {
        private final boolean invalidCredentials;

        public AuthFailedException(boolean invalidCredentials) {
            this.invalidCredentials = invalidCredentials;
        }

        public boolean isInvalidCredentials() {
            return invalidCredentials;
        }
    }

    // Firebase Authentication で認証する
    public SignInResult signIn(String email, String password) {
        try {
            Map<String, Object> res = restClient.post()
                    .uri(SIGN_IN_URL, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password, "returnSecureToken", true))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (res == null || res.get("localId") == null) {
                throw new AuthFailedException(false);
            }
            return new SignInResult(
                    (String) res.get("localId"),
                    (String) res.get("displayName"),
                    (String) res.get("email"));
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            boolean invalid = body.contains("INVALID_LOGIN_CREDENTIALS")
                    || body.contains("EMAIL_NOT_FOUND")
                    || body.contains("INVALID_PASSWORD");
            throw new AuthFailedException(invalid);
        } catch (RestClientException e) {
            throw new AuthFailedException(false);
        }
    }
}