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

    // ログイン用API
    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={key}";

    // パスワード再設定メール送信用API
    private static final String SEND_PASSWORD_RESET_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key={key}";

    // 新しいパスワード設定用API
    private static final String RESET_PASSWORD_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:resetPassword?key={key}";

    // ログイン中のユーザーのパスワード変更用API
    private static final String UPDATE_PASSWORD_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:update?key={key}";

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public FirebaseAuthRestClient(
            @Value("${firebase.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    public record SignInResult(
            String uid,
            String displayName,
            String email) {}

    // ログイン失敗時の例外
    public static class AuthFailedException extends RuntimeException {
        private final boolean invalidCredentials;

        public AuthFailedException(boolean invalidCredentials) {
            this.invalidCredentials = invalidCredentials;
        }

        public boolean isInvalidCredentials() {
            return invalidCredentials;
        }
    }

    // パスワード再設定時の例外
    public static class PasswordResetException extends RuntimeException {
        private final String errorBody;

        public PasswordResetException(String errorBody) {
            this.errorBody = errorBody;
        }

        public String getErrorBody() {
            return errorBody;
        }
    }

    // Firebase Authentication で認証する
    public SignInResult signIn(String email, String password) {
        try {
            Map<String, Object> res = restClient.post()
                    .uri(SIGN_IN_URL, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", email,
                            "password", password,
                            "returnSecureToken", true))
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

    // パスワード再設定メールを送信する
    public void sendPasswordResetEmail(String email) {
        try {
            restClient.post()
                    .uri(SEND_PASSWORD_RESET_URL, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "requestType", "PASSWORD_RESET",
                            "email", email))
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException e) {
            throw new PasswordResetException(
                    e.getResponseBodyAsString());

        } catch (RestClientException e) {
            throw new PasswordResetException(null);
        }
    }


    // パスワードを変更する
    public void changePassword(
            String email,
            String currentPassword,
            String newPassword) {

        Map<String, Object> signInResult;

        try {
            // 現在のパスワードで再認証する
            signInResult = restClient.post()
                    .uri(SIGN_IN_URL, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", email,
                            "password", currentPassword,
                            "returnSecureToken", true))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        } catch (HttpClientErrorException e) {

            // 現在のパスワードが間違っている場合
            throw new AuthFailedException(true);

        } catch (RestClientException e) {

            // 通信などで認証に失敗した場合
            throw new PasswordResetException(null);
        }

        if (signInResult == null || signInResult.get("idToken") == null) {
            throw new PasswordResetException(null);
        }

        // IDトークンを取得する
        String idToken = (String) signInResult.get("idToken");

        try {
            // 新しいパスワードに変更する
            restClient.post()
                    .uri(UPDATE_PASSWORD_URL, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "idToken", idToken,
                            "password", newPassword,
                            "returnSecureToken", true))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException e) {

            // パスワードの変更に失敗した場合
            throw new PasswordResetException(null);
        }
    }

}
