package com.payment.payment_service.user.dto;

public record PatchUserRequestDTO(
    String email,
    String password
) {}
