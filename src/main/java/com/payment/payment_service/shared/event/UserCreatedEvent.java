package com.payment.payment_service.shared.event;

import java.util.UUID;
import java.time.LocalDateTime;

public record UserCreatedEvent(
    UUID userId,
    LocalDateTime occurredAt
) {
    public UserCreatedEvent(UUID userId) {
        this(userId, LocalDateTime.now());
    }
}
