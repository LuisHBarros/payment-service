package com.payment.payment_service.transfer.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferCreatedEvent(
    UUID transferId,
    UUID sourceWalletId,
    UUID destinationWalletId,
    BigDecimal amount,
    LocalDateTime occurredAt
) {
    public TransferCreatedEvent(UUID transferId, UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount) {
        this(transferId, sourceWalletId, destinationWalletId, amount, LocalDateTime.now());
    }
}
