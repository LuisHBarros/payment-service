package com.payment.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class OpenApiIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /v3/api-docs should be public and expose bearer auth")
    void apiDocs_shouldBePublicAndExposeBearerAuth() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity("/v3/api-docs", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("paths").has("/api/v1/auth/login")).isTrue();
        assertThat(response.getBody().path("paths").has("/api/v1/transfers")).isTrue();
        assertThat(response.getBody().path("components").path("securitySchemes").path("bearerAuth").path("type").asText())
            .isEqualTo("http");
        assertThat(response.getBody().path("components").path("schemas").has("ApiErrorResponse")).isTrue();
        assertThat(response.getBody().path("security")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /v3/api-docs should keep public endpoints without auth requirement")
    void apiDocs_shouldKeepPublicEndpointsWithoutAuthRequirement() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity("/v3/api-docs", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loginSecurity = response.getBody()
            .path("paths")
            .path("/api/v1/auth/login")
            .path("post")
            .path("security");
        JsonNode webhookSecurity = response.getBody()
            .path("paths")
            .path("/api/v1/webhooks/deposits")
            .path("post")
            .path("security");

        assertThat(loginSecurity.isArray()).isTrue();
        assertThat(loginSecurity).isEmpty();
        assertThat(webhookSecurity.isArray()).isTrue();
        assertThat(webhookSecurity).isEmpty();
    }

    @Test
    @DisplayName("GET /swagger-ui/index.html should be public")
    void swaggerUi_shouldBePublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }

    @Test
    @DisplayName("GET /api/v1/auth/me without token should still return 401")
    void protectedEndpoints_shouldStillRequireAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
