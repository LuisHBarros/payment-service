package com.payment.payment_service.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "CreateUserRequest", description = "Payload used to create a new user")
public record CreateUserRequestDTO(
    @Schema(description = "Full name", example = "Ada Lovelace")
    @NotBlank String name,
    @Schema(description = "Unique email address", example = "ada@example.com")
    @NotBlank String email,
    @Schema(description = "Plain-text password", example = "Password123!")
    @NotBlank String password,
    @Schema(description = "Brazilian CPF or CNPJ used to derive the user type", example = "12345678901")
    @NotBlank String document
) {}
