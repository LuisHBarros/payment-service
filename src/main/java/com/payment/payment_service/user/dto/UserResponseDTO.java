package com.payment.payment_service.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.user.type.UserType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserResponse", description = "User representation returned by the API")
public record UserResponseDTO(
    @Schema(description = "User identifier")
    UUID id,
    @Schema(description = "Full name")
    String name,
    @Schema(description = "Email address")
    String email,
    @Schema(description = "Masked document value")
    String document,
    @Schema(description = "Derived user type")
    UserType type,
    @Schema(description = "Whether the user is active")
    boolean active,
    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt,
    @Schema(description = "Last update timestamp")
    LocalDateTime updatedAt
) {}
