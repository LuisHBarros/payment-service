package com.payment.payment_service.config;

import java.util.UUID;

import com.payment.payment_service.user.type.UserType;

public record AuthenticatedUser(UUID userId, String email, UserType userType) {
    
}
