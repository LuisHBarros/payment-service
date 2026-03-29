package com.payment.payment_service.wallet.provider;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.wallet.exception.PaymentProviderException;
import com.payment.payment_service.wallet.exception.WebhookSignatureException;
import com.payment.payment_service.wallet.type.DepositStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("STRIPE")
@RequiredArgsConstructor
public class StripePaymentProvider implements PaymentProvider {

    private static final String CURRENCY = "brl";

    private final ObjectMapper objectMapper;

    @Override
    public PaymentProviderResponse createDeposit(BigDecimal amount, UUID userId, UUID walletId) {
        var params = new PaymentIntentCreateParams.Builder()
            .setAmount(amount.movePointRight(2).longValueExact())
            .setCurrency(CURRENCY)
            .putMetadata("user_id", userId.toString())
            .putMetadata("wallet_id", walletId.toString())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build())
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
            .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Created Stripe PaymentIntent intent={} userId={} walletId={}",
                intent.getId(), userId, walletId);

            // Create a simplified JSON object with only primitive values
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("id", intent.getId());
            responseMap.put("amount", intent.getAmount());
            responseMap.put("currency", intent.getCurrency());
            responseMap.put("status", intent.getStatus() != null ? intent.getStatus().toString() : null);
            responseMap.put("clientSecret", intent.getClientSecret());

            // Handle metadata safely - convert to simple map
            Map<String, String> metadataMap = new HashMap<>();
            if (intent.getMetadata() != null) {
                metadataMap.putAll(intent.getMetadata());
            }
            responseMap.put("metadata", metadataMap);

            // Handle created timestamp
            responseMap.put("created", intent.getCreated() != null ? intent.getCreated() : null);

            String rawResponse = objectMapper.writeValueAsString(responseMap);

            return new PaymentProviderResponse(
                intent.getClientSecret(),
                intent.getId(),
                mapStripeStatus(intent.getStatus()),
                rawResponse
            );
        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent userId={} walletId={}", userId, walletId, e);
            throw new PaymentProviderException("Error creating Stripe deposit", e);
        } catch (JsonProcessingException e) {
            log.error("Serialization error for PaymentIntent userId={} walletId={}", userId, walletId, e);
            throw new PaymentProviderException("Error serializing payment response", e);
        }
    }

    @Override
    public Optional<WebhookResult> parseWebhookEvent(String payload, String signature, String secret) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            throw new WebhookSignatureException("Invalid Stripe webhook signature");
        }

        DepositStatus status = mapStripeEventType(event.getType());
        if (status == null) {
            log.info("Ignoring unsupported Stripe event type={}", event.getType());
            return Optional.empty();
        }
        String paymentIntentId;
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
             paymentIntentId = ((PaymentIntent) deserializer.getObject().get()).getId();
        } else {
            log.warn("API version mismatch for event type={}, falling back to raw JSON deserialization", event.getType());
            try {
                String rawJson = event.getData().toJson();
                paymentIntentId = objectMapper.readTree(rawJson).path("object").path("id").asText();
                if (paymentIntentId == null || paymentIntentId.isEmpty()) {
                    throw new PaymentProviderException(
                        "Could not extract PaymentIntent ID from Stripe event type=" + event.getType(), null);
                }
            } catch (JsonProcessingException e) {
                throw new PaymentProviderException(
                    "Could not deserialize PaymentIntent from Stripe event type=" + event.getType(), e);
            }
        }


        return Optional.of(new WebhookResult(paymentIntentId, status));
    }

    private DepositStatus mapStripeEventType(String eventType) {
        return switch (eventType) {
            case "payment_intent.succeeded"      -> DepositStatus.SUCCESS;
            case "payment_intent.payment_failed" -> DepositStatus.FAILED;
            case "payment_intent.canceled"       -> DepositStatus.CANCELED;
            default                              -> null;
        };
    }

    private DepositStatus mapStripeStatus(String status) {
        return switch (status) {
            case "succeeded"                 -> DepositStatus.SUCCESS;
            case "canceled"                  -> DepositStatus.CANCELED;
            case "requires_payment_method",
                 "requires_confirmation",
                 "requires_action",
                 "processing"                -> DepositStatus.PENDING;
            default                          -> DepositStatus.PENDING;
        };
    }
}
