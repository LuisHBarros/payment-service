package com.payment.payment_service.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.auth.JwtAuthenticationFilter;
import com.payment.payment_service.auth.service.JwtService;
import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.wallet.dto.CreateDepositRequestDTO;
import com.payment.payment_service.wallet.dto.DepositResponseDTO;
import com.payment.payment_service.wallet.exception.WebhookSignatureException;
import com.payment.payment_service.wallet.provider.PaymentProvider;
import com.payment.payment_service.wallet.provider.WebhookResult;
import com.payment.payment_service.wallet.service.CreateDepositService;
import com.payment.payment_service.wallet.service.ProcessDepositService;
import com.payment.payment_service.wallet.type.DepositStatus;

@SuppressWarnings("null")
@WebMvcTest(DepositController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DepositControllerTest.TestConfig.class)
@TestPropertySource(properties = {
    "payment.provider=STRIPE",
    "payment.webhook_secret=whsec_test",
    "payment.webhook_header_name=Stripe-Signature"
})
class DepositControllerTest {

    static class TestConfig {
        @Bean("STRIPE")
        PaymentProvider paymentProvider() {
            return mock(PaymentProvider.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateDepositService createDepositService;

    @MockitoBean
    private ProcessDepositService processDepositService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private PaymentProvider paymentProvider;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        var authenticatedUser = new AuthenticatedUser(userId, "test@example.com", UserType.COMMON);
        var authentication = new UsernamePasswordAuthenticationToken(
            authenticatedUser, null,
            List.of(new SimpleGrantedAuthority("ROLE_COMMON")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        reset(createDepositService, processDepositService, paymentProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDeposit_WithValidRequest_ShouldReturn201() throws Exception {
        // Arrange
        CreateDepositRequestDTO request = new CreateDepositRequestDTO(
            new BigDecimal("50.00"), UUID.randomUUID(), com.payment.payment_service.wallet.type.PaymentProviderName.STRIPE);
        DepositResponseDTO response = new DepositResponseDTO(
            UUID.randomUUID(), "cs_test_secret", DepositStatus.PENDING, new BigDecimal("50.00"));
        when(createDepositService.execute(eq(userId), any(CreateDepositRequestDTO.class)))
            .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/wallets/{userId}/deposits", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.depositId").value(response.depositId().toString()))
            .andExpect(jsonPath("$.clientSecret").value("cs_test_secret"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void createDeposit_WithMissingAmount_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{\"walletId\":\"" + UUID.randomUUID() + "\",\"paymentProvider\":\"STRIPE\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/wallets/{userId}/deposits", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createDeposit_WithMissingWalletId_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{\"amount\":50.00,\"paymentProvider\":\"STRIPE\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/wallets/{userId}/deposits", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createDeposit_WithMissingPaymentProvider_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{\"amount\":50.00,\"walletId\":\"" + UUID.randomUUID() + "\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/wallets/{userId}/deposits", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void handleWebhook_WithValidPayload_ShouldReturn200() throws Exception {
        // Arrange
        WebhookResult webhookResult = new WebhookResult("pi_test_123", DepositStatus.SUCCESS);
        when(paymentProvider.parseWebhookEvent(anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(webhookResult));
        doNothing().when(processDepositService).execute(anyString(), anyString(), any(DepositStatus.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=123,v1=abc")
                .content("{\"type\":\"payment_intent.succeeded\"}"))
            .andExpect(status().isOk());

        verify(processDepositService).execute("pi_test_123", "STRIPE", DepositStatus.SUCCESS);
    }

    @Test
    void handleWebhook_WithInvalidSignature_ShouldReturn401() throws Exception {
        // Arrange
        when(paymentProvider.parseWebhookEvent(anyString(), anyString(), anyString()))
            .thenThrow(new WebhookSignatureException("Invalid signature"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "bad_signature")
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verify(processDepositService, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void handleWebhook_WithUnsupportedEvent_ShouldReturn200() throws Exception {
        // Arrange
        when(paymentProvider.parseWebhookEvent(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/api/v1/webhooks/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=123,v1=abc")
                .content("{\"type\":\"payment_intent.created\"}"))
            .andExpect(status().isOk());

        verify(processDepositService, never()).execute(anyString(), anyString(), any());
    }
}
