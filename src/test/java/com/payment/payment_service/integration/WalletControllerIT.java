package com.payment.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.payment.payment_service.user.type.UserType;

class WalletControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /wallets/{userId} as owner should return wallet")
    void getWallet_asOwner_shouldReturnWallet() {
        UUID userId = testHelper.createCommonUser("WalletOwner", "wallet.owner@example.com", "Pass123!");
        testHelper.createWallet(userId, java.math.BigDecimal.TEN);

        HttpHeaders headers = testHelper.authHeaders(userId, "wallet.owner@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<com.payment.payment_service.wallet.dto.WalletResponseDTO> response = restTemplate.exchange(
            "/api/v1/wallets/{userId}", HttpMethod.GET, entity,
            com.payment.payment_service.wallet.dto.WalletResponseDTO.class, userId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        com.payment.payment_service.wallet.dto.WalletResponseDTO body =
            Objects.requireNonNull(response.getBody());
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.balance()).isEqualByComparingTo(java.math.BigDecimal.TEN);
    }

    @Test
    @DisplayName("GET /wallets/{userId} as non-owner should return 403")
    void getWallet_asNonOwner_shouldReturn403() {
        UUID ownerId = testHelper.createCommonUser("WalletOwn", "wallet.own@example.com", "Pass123!");
        UUID otherId = testHelper.createCommonUser("Other", "wallet.other@example.com", "Pass123!");
        testHelper.createWallet(ownerId, java.math.BigDecimal.TEN);

        HttpHeaders headers = testHelper.authHeaders(otherId, "wallet.other@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/wallets/{userId}", HttpMethod.GET, entity,
            String.class, ownerId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /wallets/{userId} when user has no wallet should return 404")
    void getWallet_notFound_shouldReturn404() {
        UUID userId = testHelper.createCommonUser("NoWallet", "wallet.nowallet@example.com", "Pass123!");

        HttpHeaders headers = testHelper.authHeaders(userId, "wallet.nowallet@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/wallets/{userId}", HttpMethod.GET, entity,
            String.class, userId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /wallets/{userId} without auth should return 401")
    void getWallet_withoutAuth_shouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/wallets/{userId}", String.class, UUID.randomUUID()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
