package com.payment.payment_service.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PatchUserRequest", description = "Partial user update payload")
public record PatchUserRequestDTO(
    @Schema(description = "New email address", example = "new.email@example.com")
    String email,
    @Schema(description = "New password", example = "NewPassword123!")
    String password
) {}
