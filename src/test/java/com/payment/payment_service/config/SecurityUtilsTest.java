package com.payment.payment_service.config;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.payment.payment_service.user.type.UserType;

class SecurityUtilsTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID anotherId = UUID.randomUUID();

    @Test
    @DisplayName("should not throw when userId matches resourceId")
    void shouldPass_whenUserIdMatchesResourceId() {
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@test.com", UserType.COMMON);

        assertThatNoException().isThrownBy(() -> SecurityUtils.requireOwnership(user, userId));
    }

    @Test
    @DisplayName("should not throw for admin with different resourceId")
    void shouldPass_forAdmin_withDifferentResourceId() {
        AuthenticatedUser admin = new AuthenticatedUser(userId, "admin@test.com", UserType.ADMIN);

        assertThatNoException().isThrownBy(() -> SecurityUtils.requireOwnership(admin, anotherId));
    }

    @Test
    @DisplayName("should throw AccessDeniedException for common user with different resourceId")
    void shouldThrow_forCommonUser_withDifferentResourceId() {
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@test.com", UserType.COMMON);

        assertThatThrownBy(() -> SecurityUtils.requireOwnership(user, anotherId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("Access denied");
    }

    @Test
    @DisplayName("should throw AccessDeniedException for merchant user with different resourceId")
    void shouldThrow_forMerchantUser_withDifferentResourceId() {
        AuthenticatedUser merchant = new AuthenticatedUser(userId, "merchant@test.com", UserType.MERCHANT);

        assertThatThrownBy(() -> SecurityUtils.requireOwnership(merchant, anotherId))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("Access denied");
    }
}
