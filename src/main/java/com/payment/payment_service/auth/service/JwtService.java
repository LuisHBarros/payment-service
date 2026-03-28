package com.payment.payment_service.auth.service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.payment.payment_service.user.entity.UserEntity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {
    
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public long getExpirationSeconds() {
        return expiration;
    }

    public String generateToken(UserEntity user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail().value())
            .claim("type", user.getType().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expiration)))
            .signWith(getSigningKey())
            .id(UUID.randomUUID().toString())
            .compact();
    }
    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getJti(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)  // lança se expirado
            .getPayload()
            .getId();
    }

    public long getRemainingSeconds(Claims claims) {
        return (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
    }
}
