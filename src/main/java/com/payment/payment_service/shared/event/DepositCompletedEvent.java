package com.payment.payment_service.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DepositCompletedEvent(
    UUID depositId,
    UUID walletId,
    UUID userId,
    BigDecimal amount,
    String provider,
    LocalDateTime occurredAt
) {
    public DepositCompletedEvent(UUID depositId, UUID walletId, UUID userId,
                                  BigDecimal amount, String provider) {
        this(depositId, walletId, userId, amount, provider, LocalDateTime.now());
    }
}