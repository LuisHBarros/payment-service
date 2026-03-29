package com.payment.payment_service.wallet.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.wallet.exception.PaymentProviderException;
import com.payment.payment_service.wallet.exception.WebhookSignatureException;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StripePaymentProviderTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StripePaymentProvider stripePaymentProvider;

    private MockedStatic<PaymentIntent> paymentIntentMock;
    private MockedStatic<Webhook> webhookMock;

    @BeforeEach
    void setUp() {
        paymentIntentMock = mockStatic(PaymentIntent.class);
        webhookMock = mockStatic(Webhook.class);
    }

    @AfterEach
    void tearDown() {
        paymentIntentMock.close();
        webhookMock.close();
    }

    @Test
    void createDeposit_WithSuccessfulResponse_ShouldReturnPaymentProviderResponse() throws Exception {
        // Arrange
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        paymentIntentMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
            .thenReturn(mockIntent);

        when(mockIntent.getClientSecret()).thenReturn("cs_test_secret");
        when(mockIntent.getId()).thenReturn("pi_test_123");
        when(mockIntent.getStatus()).thenReturn("requires_payment_method");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        PaymentProviderResponse response = stripePaymentProvider.createDeposit(
            new BigDecimal("50.00"), UUID.randomUUID(), UUID.randomUUID());

        // Assert
        assertNotNull(response);
        assertEquals("cs_test_secret", response.clientSecret());
        assertEquals("pi_test_123", response.externalPaymentReference());
        assertEquals(DepositStatus.PENDING, response.status());
        assertEquals("{}", response.rawResponse());
    }

    @Test
    void createDeposit_WithStripeException_ShouldThrowPaymentProviderException() {
        // Arrange
        StripeException stripeException = mock(StripeException.class);
        paymentIntentMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
            .thenThrow(stripeException);

        // Act & Assert
        assertThrows(PaymentProviderException.class, () ->
            stripePaymentProvider.createDeposit(
                new BigDecimal("50.00"), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void createDeposit_WithSerializationError_ShouldThrowPaymentProviderException() throws Exception {
        // Arrange
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        paymentIntentMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
            .thenReturn(mockIntent);
        when(mockIntent.getClientSecret()).thenReturn("cs_secret");
        when(mockIntent.getId()).thenReturn("pi_123");
        when(mockIntent.getStatus()).thenReturn("requires_payment_method");
        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new JsonParseException(null, "serialization error"));

        // Act & Assert
        assertThrows(PaymentProviderException.class, () ->
            stripePaymentProvider.createDeposit(
                new BigDecimal("50.00"), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void parseWebhookEvent_WithSuccessEvent_ShouldReturnWebhookResult() throws Exception {
        // Arrange
        Event mockEvent = mock(Event.class);
        webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
            .thenReturn(mockEvent);

        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");

        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn("pi_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(mockPaymentIntent));

        // Act
        Optional<WebhookResult> result = stripePaymentProvider.parseWebhookEvent(
            "{}", "t=123,v1=abc", "whsec_test");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("pi_test_123", result.get().externalReference());
        assertEquals(DepositStatus.SUCCESS, result.get().status());
    }

    @Test
    void parseWebhookEvent_WithUnsupportedEventType_ShouldReturnEmpty() throws Exception {
        // Arrange
        Event mockEvent = mock(Event.class);
        webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
            .thenReturn(mockEvent);
        when(mockEvent.getType()).thenReturn("payment_intent.created");

        // Act
        Optional<WebhookResult> result = stripePaymentProvider.parseWebhookEvent(
            "{}", "t=123,v1=abc", "whsec_test");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parseWebhookEvent_WithInvalidSignature_ShouldThrowWebhookSignatureException() {
        // Arrange
        webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
            .thenThrow(mock(SignatureVerificationException.class));

        // Act & Assert
        assertThrows(WebhookSignatureException.class, () ->
            stripePaymentProvider.parseWebhookEvent("{}", "bad_sig", "whsec_test"));
    }
}
