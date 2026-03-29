package com.payment.payment_service.wallet.provider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProvider {
    PaymentProviderResponse createDeposit(BigDecimal amount, UUID userId, UUID walletId);
    Optional<WebhookResult> parseWebhookEvent(String payload, String signature, String secret);
}
