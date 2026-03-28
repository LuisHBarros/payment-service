package com.payment.payment_service.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.user.type.UserType;

public record UserResponseDTO(
    UUID id,
    String name,
    String email,
    String document,
    UserType type,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
