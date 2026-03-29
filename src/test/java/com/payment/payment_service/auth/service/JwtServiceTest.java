package com.payment.payment_service.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Email;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtService wrongKeyService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);

        wrongKeyService = new JwtService();
        ReflectionTestUtils.setField(wrongKeyService, "secret", "dGVzdHNlY3JldGtleXdoaWNoaXNkaWZmZXJlbnQxMjM=");
        ReflectionTestUtils.setField(wrongKeyService, "expiration", 86400000L);
    }

    private UserEntity buildUser(UUID id, String email, UserType type) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(new Email(email));
        user.setType(type);
        return user;
    }

    @Test
    @DisplayName("generateToken should create valid token with correct claims")
    void generateToken_shouldCreateValidToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "test@example.com", UserType.COMMON);

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();

        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
        assertThat(claims.get("type", String.class)).isEqualTo("COMMON");
        assertThat(claims.getId()).isNotNull();
    }

    @Test
    @DisplayName("validateToken should return claims for valid token")
    void validateToken_shouldReturnClaims_forValidToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "valid@example.com", UserType.MERCHANT);

        String token = jwtService.generateToken(user);
        Claims claims = jwtService.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("type", String.class)).isEqualTo("MERCHANT");
    }

    @Test
    @DisplayName("validateToken should throw JwtException for expired token")
    void validateToken_shouldThrow_forExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService();
        ReflectionTestUtils.setField(shortLived, "secret", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        ReflectionTestUtils.setField(shortLived, "expiration", 1L);

        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "expire@example.com", UserType.COMMON);
        String token = shortLived.generateToken(user);

        Thread.sleep(100);

        assertThatThrownBy(() -> shortLived.validateToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("validateToken should throw JwtException for tampered token")
    void validateToken_shouldThrow_forTamperedToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "tamper@example.com", UserType.COMMON);
        String token = jwtService.generateToken(user);

        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("validateToken should throw JwtException when signed with different key")
    void validateToken_shouldThrow_forWrongSignerKey() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "wrongkey@example.com", UserType.COMMON);
        String token = jwtService.generateToken(user);

        assertThatThrownBy(() -> wrongKeyService.validateToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("getJti should extract token id from valid token")
    void getJti_shouldExtractTokenId() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "jti@example.com", UserType.COMMON);
        String token = jwtService.generateToken(user);

        String jti = jwtService.getJti(token);

        assertThat(jti).isNotNull();
        assertDoesNotThrow(() -> UUID.fromString(jti));
    }

    @Test
    @DisplayName("getRemainingSeconds should return positive value for valid token")
    void getRemainingSeconds_shouldReturnPositive_forValidToken() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "remain@example.com", UserType.COMMON);
        String token = jwtService.generateToken(user);

        Claims claims = jwtService.validateToken(token);
        long remaining = jwtService.getRemainingSeconds(claims);

        assertThat(remaining).isGreaterThan(0);
        assertThat(remaining).isLessThanOrEqualTo(86400);
    }
}
