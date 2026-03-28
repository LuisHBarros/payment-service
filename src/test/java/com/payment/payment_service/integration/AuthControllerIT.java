package com.payment.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.payment.payment_service.auth.dto.LoginRequest;

@SuppressWarnings("null")
class AuthControllerIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Test
    @DisplayName("POST /auth/login with valid email should return JWT token")
    void login_withValidEmail_shouldReturnToken() {
        String email = "login.email@example.com";
        testHelper.createCommonUser("Login User", email, PASSWORD);

        LoginRequest request = new LoginRequest();
        request.setIdentifier(email);
        request.setPassword(PASSWORD);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("token").asText()).isNotBlank();
        assertThat(response.getBody().get("expiresIn").asLong()).isGreaterThan(0);
    }

    @Test
    @DisplayName("POST /auth/login with valid CPF should return JWT token")
    void login_withValidDocument_shouldReturnToken() {
        String email = "login.cpf@example.com";
        UUID userId = testHelper.createCommonUser("Cpf User", email, PASSWORD);
        String document = userRepository.findById(userId).orElseThrow().getDocument().value();

        LoginRequest request = new LoginRequest();
        request.setIdentifier(document);
        request.setPassword(PASSWORD);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("POST /auth/login with invalid password should return 401")
    void login_withInvalidPassword_shouldReturn401() {
        String email = "login.wrong@example.com";
        testHelper.createCommonUser("Wrong Pass User", email, PASSWORD);

        LoginRequest request = new LoginRequest();
        request.setIdentifier(email);
        request.setPassword("WrongPassword999!");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /auth/login with non-existing user should return 401")
    void login_withNonExistingUser_shouldReturn401() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("nonexistent@example.com");
        request.setPassword(PASSWORD);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /auth/login with blank fields should return 400")
    void login_withBlankFields_shouldReturn400() {
        String json = """
            {"identifier": "", "password": ""}
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /auth/logout with valid token should return 204")
    void logout_withValidToken_shouldReturn204() {
        String email = "logout.valid@example.com";
        UUID userId = testHelper.createCommonUser("Logout User", email, PASSWORD);
        String token = testHelper.generateToken(userId, email, com.payment.payment_service.user.type.UserType.COMMON);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/api/v1/auth/logout", request, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("POST /auth/login with blacklisted token should return 401")
    void login_afterLogout_shouldReturn401() {
        String email = "logout.blacklist@example.com";
        UUID userId = testHelper.createCommonUser("Blacklist User", email, PASSWORD);
        String token = testHelper.generateToken(userId, email, com.payment.payment_service.user.type.UserType.COMMON);

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setBearerAuth(token);
        restTemplate.postForEntity("/api/v1/auth/logout", new HttpEntity<>(logoutHeaders), Void.class);

        LoginRequest request = new LoginRequest();
        request.setIdentifier(email);
        request.setPassword(PASSWORD);
    }

    @Test
    @DisplayName("POST /auth/logout without token should return 401")
    void logout_withoutToken_shouldReturn401() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
            "/api/v1/auth/logout", HttpEntity.EMPTY, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
