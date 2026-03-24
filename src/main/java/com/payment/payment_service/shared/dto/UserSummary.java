package com.payment.payment_service.shared.dto;

import java.util.UUID;

import com.payment.payment_service.user.type.UserType;

public record UserSummary(
    UUID id,
    UserType type,
    boolean active
) {
    public boolean canSend() { 
        return type == UserType.COMMON && active; 
    }
    public boolean canReceive() { 
        return type == UserType.MERCHANT && active; 
    }
}
