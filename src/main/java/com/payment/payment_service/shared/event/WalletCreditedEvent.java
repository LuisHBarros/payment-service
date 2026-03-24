package com.payment.payment_service.shared.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletCreditedEvent(
    UUID walletId,
    UUID transferId,
    BigDecimal amount,
    LocalDateTime occurredAt
) {
    public WalletCreditedEvent(UUID walletId, UUID transferId, BigDecimal amount) {
        this(walletId, transferId, amount, LocalDateTime.now());
    }
}