package com.payment.payment_service.shared.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.shared.type.TransferStatus;

public record TransferStatusChangedEvent(
    UUID transferId,
    TransferStatus status,
    LocalDateTime occurredAt
) {
    public TransferStatusChangedEvent(UUID transferId, TransferStatus status) {
        this(transferId, status, LocalDateTime.now());
    }
}