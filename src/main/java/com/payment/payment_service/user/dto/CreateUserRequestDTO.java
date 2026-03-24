package com.payment.payment_service.user.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequestDTO(
    @NotBlank String name,
    @NotBlank String email,
    @NotBlank String password,
    @NotBlank String document
) {}
