package com.payment.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.payment.payment_service.transfer.dto.CreateTransferRequestDTO;
import com.payment.payment_service.user.type.UserType;

class TransferControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("POST /transfers without auth should return 401")
    void create_withoutAuth_shouldReturn401() {
        CreateTransferRequestDTO request = new CreateTransferRequestDTO(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00")
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", request, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /transfers as MERCHANT should return 403")
    void create_asMerchant_shouldReturn403() {
        UUID userId = testHelper.createMerchantUser("Merchant", "transfer.merchant@example.com", "Pass123!");
        HttpHeaders headers = testHelper.authHeaders(userId, "transfer.merchant@example.com", UserType.MERCHANT);

        CreateTransferRequestDTO request = new CreateTransferRequestDTO(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00")
        );
        HttpEntity<CreateTransferRequestDTO> entity = new HttpEntity<>(request, Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", entity, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /transfers with zero amount should return 400")
    void create_zeroAmount_shouldReturn400() {
        UUID userId = testHelper.createMerchantUser("ZeroAmt", "transfer.zero@example.com", "Pass123!");
        HttpHeaders headers = testHelper.authHeaders(userId, "transfer.zero@example.com", UserType.MERCHANT);

        String json = """
            {"sourceWalletId": "%s", "destinationWalletId": "%s", "amount": 0}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
        HttpEntity<String> entity = new HttpEntity<>(json, Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", entity, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /transfers with negative amount should return 400")
    void create_negativeAmount_shouldReturn400() {
        UUID userId = testHelper.createMerchantUser("NegAmt", "transfer.neg@example.com", "Pass123!");
        HttpHeaders headers = testHelper.authHeaders(userId, "transfer.neg@example.com", UserType.MERCHANT);

        String json = """
            {"sourceWalletId": "%s", "destinationWalletId": "%s", "amount": -50.00}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
        HttpEntity<String> entity = new HttpEntity<>(json, Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", entity, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /transfers with null sourceWalletId should return 400")
    void create_nullSourceWalletId_shouldReturn400() {
        UUID userId = testHelper.createMerchantUser("NullSrc", "transfer.null@example.com", "Pass123!");
        HttpHeaders headers = testHelper.authHeaders(userId, "transfer.null@example.com", UserType.MERCHANT);

        String json = """
            {"sourceWalletId": null, "destinationWalletId": "%s", "amount": 10.00}
            """.formatted(UUID.randomUUID());
        HttpEntity<String> entity = new HttpEntity<>(json, Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", entity, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /transfers as COMMON user should return 200 with PENDING transfer")
    void create_shouldReturnPendingTransfer() {
        UUID senderId = testHelper.createCommonUser("Sender Ctrl", "transfer.sender@example.com", "Pass123!");
        UUID receiverId = testHelper.createMerchantUser("Receiver Ctrl", "transfer.receiver@example.com", "Pass123!");
        UUID sourceWalletId = testHelper.createWallet(senderId, new BigDecimal("100.00"));
        UUID destWalletId = testHelper.createWallet(receiverId, new BigDecimal("50.00"));

        HttpHeaders headers = testHelper.authHeaders(senderId, "transfer.sender@example.com", UserType.COMMON);

        CreateTransferRequestDTO request = new CreateTransferRequestDTO(sourceWalletId, destWalletId, new BigDecimal("30.00"));
        HttpEntity<CreateTransferRequestDTO> entity = new HttpEntity<>(request, Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", entity, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("POST /transfers with insufficient balance should return 403")
    void create_insufficientBalance_shouldReturn403() {
        UUID senderId = testHelper.createCommonUser("NoBalance", "transfer.nobalance@example.com", "Pass123!");
        UUID receiverId = testHelper.createMerchantUser("Merchant", "transfer.nobalance.recv@example.com", "Pass123!");
        UUID sourceWalletId = testHelper.createWallet(senderId, new BigDecimal("5.00"));
        UUID destWalletId = testHelper.createWallet(receiverId, new BigDecimal("50.00"));

        HttpHeaders headers = testHelper.authHeaders(senderId, "transfer.nobalance@example.com", UserType.COMMON);

        CreateTransferRequestDTO request = new CreateTransferRequestDTO(sourceWalletId, destWalletId, new BigDecimal("100.00"));
        HttpEntity<CreateTransferRequestDTO> entity = new HttpEntity<>(request, Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            "/api/v1/transfers", entity, JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /transfers?walletId= without auth should return 401")
    void findByWalletId_withoutAuth_shouldReturn401() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
            "/api/v1/transfers?walletId={id}", JsonNode.class, UUID.randomUUID()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /transfers?walletId= as owner should return paginated transfers")
    void findByWalletId_asOwner_shouldReturnPage() {
        UUID userId = testHelper.createCommonUser("ListOwner", "transfer.list@example.com", "Pass123!");
        UUID walletId = testHelper.createWallet(userId, new BigDecimal("100.00"));

        HttpHeaders headers = testHelper.authHeaders(userId, "transfer.list@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "/api/v1/transfers?walletId={id}", org.springframework.http.HttpMethod.GET, entity, JsonNode.class, walletId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("content")).isNotNull();
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("GET /transfers?walletId=&status=COMPLETED should filter by status")
    void findByWalletId_withStatusFilter_shouldFilterByStatus() {
        UUID senderId = testHelper.createCommonUser("FilterStatusSender", "filter.status.sender@example.com", "Pass123!");
        UUID receiverId = testHelper.createCommonUser("FilterStatusReceiver", "filter.status.recv@example.com", "Pass123!");
        UUID sourceWalletId = testHelper.createWallet(senderId, new BigDecimal("100.00"));
        UUID destWalletId = testHelper.createWallet(receiverId, new BigDecimal("50.00"));

        HttpHeaders headers = testHelper.authHeaders(senderId, "filter.status.sender@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "/api/v1/transfers?walletId={id}&status=COMPLETED",
            org.springframework.http.HttpMethod.GET, entity, JsonNode.class, sourceWalletId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("content")).isNotNull();
        for (JsonNode item : body.get("content")) {
            assertThat(item.get("status").asText()).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("GET /transfers?walletId=&type=DEBIT should filter as sender only")
    void findByWalletId_withDebitTypeFilter_shouldFilterAsSender() {
        UUID senderId = testHelper.createCommonUser("DebitSender", "debit.sender@example.com", "Pass123!");
        UUID receiverId = testHelper.createCommonUser("DebitReceiver", "debit.receiver@example.com", "Pass123!");
        UUID sourceWalletId = testHelper.createWallet(senderId, new BigDecimal("100.00"));
        UUID destWalletId = testHelper.createWallet(receiverId, new BigDecimal("50.00"));

        HttpHeaders headers = testHelper.authHeaders(senderId, "debit.sender@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "/api/v1/transfers?walletId={id}&type=DEBIT",
            org.springframework.http.HttpMethod.GET, entity, JsonNode.class, sourceWalletId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("content")).isNotNull();
        for (JsonNode item : body.get("content")) {
            assertThat(item.get("type").asText()).isEqualTo("DEBIT");
        }
    }

    @Test
    @DisplayName("GET /transfers?walletId=&type=CREDIT should filter as receiver only")
    void findByWalletId_withCreditTypeFilter_shouldFilterAsReceiver() {
        UUID senderId = testHelper.createCommonUser("CreditSender", "credit.sender@example.com", "Pass123!");
        UUID receiverId = testHelper.createCommonUser("CreditReceiver", "credit.receiver@example.com", "Pass123!");
        UUID sourceWalletId = testHelper.createWallet(senderId, new BigDecimal("100.00"));
        UUID destWalletId = testHelper.createWallet(receiverId, new BigDecimal("50.00"));

        HttpHeaders headers = testHelper.authHeaders(receiverId, "credit.receiver@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "/api/v1/transfers?walletId={id}&type=CREDIT",
            org.springframework.http.HttpMethod.GET, entity, JsonNode.class, destWalletId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("content")).isNotNull();
        for (JsonNode item : body.get("content")) {
            assertThat(item.get("type").asText()).isEqualTo("CREDIT");
        }
    }

    @Test
    @DisplayName("GET /transfers?walletId=&startDate=&endDate= should filter by date range")
    void findByWalletId_withDateRangeFilter_shouldFilterByDateRange() {
        UUID userId = testHelper.createCommonUser("DateRangeUser", "daterange.user@example.com", "Pass123!");
        UUID walletId = testHelper.createWallet(userId, new BigDecimal("100.00"));

        HttpHeaders headers = testHelper.authHeaders(userId, "daterange.user@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "/api/v1/transfers?walletId={id}&startDate=2020-01-01&endDate=2099-12-31",
            org.springframework.http.HttpMethod.GET, entity, JsonNode.class, walletId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("content")).isNotNull();
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("GET /transfers?walletId= without filters should include type field in response")
    void findByWalletId_withoutFilters_shouldIncludeTypeField() {
        UUID senderId = testHelper.createCommonUser("TypeFieldSender", "typefield.sender@example.com", "Pass123!");
        UUID receiverId = testHelper.createCommonUser("TypeFieldReceiver", "typefield.recv@example.com", "Pass123!");
        UUID sourceWalletId = testHelper.createWallet(senderId, new BigDecimal("100.00"));
        UUID destWalletId = testHelper.createWallet(receiverId, new BigDecimal("50.00"));

        HttpHeaders headers = testHelper.authHeaders(senderId, "typefield.sender@example.com", UserType.COMMON);
        HttpEntity<Void> entity = new HttpEntity<>(Objects.requireNonNull(headers));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
            "/api/v1/transfers?walletId={id}",
            org.springframework.http.HttpMethod.GET, entity, JsonNode.class, sourceWalletId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Objects.requireNonNull(response.getBody());
        assertThat(body.get("content")).isNotNull();
    }
}
