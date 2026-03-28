package com.payment.payment_service.config;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import com.payment.payment_service.user.type.UserType;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static void requireOwnership(AuthenticatedUser auth, UUID resourceId) {
        if (auth.userType() != UserType.ADMIN && !auth.userId().equals(resourceId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}