package com.payment.payment_service.auth.service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.payment.payment_service.shared.crypto.HashUtil;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "invalid credentials";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtService jwtService;

    public UserEntity authenticate(String identifier, String rawPassword) {
        Optional<UserEntity> user = findUserByIdentifier(identifier);

        UserEntity entity = user.orElseThrow(() ->
            new BadCredentialsException(INVALID_CREDENTIALS)
        );

        if (!passwordEncoder.matches(rawPassword, entity.getPassword())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        return entity;
    }

    private Optional<UserEntity> findUserByIdentifier(String identifier) {
        if (Email.isValid(identifier)) {
            return userRepository.findByEmail(new Email(identifier));
        }

        if (Document.isValid(identifier)) {
            String documentHash = HashUtil.sha256(new Document(identifier).value());
            return userRepository.findByDocumentHash(documentHash);
        }

        return Optional.empty();
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Authorization header");
        }
        String token = authHeader.substring(7);
        Claims claims = jwtService.validateToken(token);
        String jti = claims.getId();

        // TTL = tempo restante do token para a chave expirar junto com o token
        long remainingSeconds = jwtService.getRemainingSeconds(claims);

        if (remainingSeconds > 0) {
            stringRedisTemplate.opsForValue()
                .set("blacklist:" + jti, "1", Objects.requireNonNull(Duration.ofSeconds(remainingSeconds)));
        }
    }
}
