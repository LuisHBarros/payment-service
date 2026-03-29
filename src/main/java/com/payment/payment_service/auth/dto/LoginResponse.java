package com.payment.payment_service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(name = "LoginResponse", description = "JWT token returned after successful authentication")
public class LoginResponse {
    @Schema(description = "Bearer token to be sent in the Authorization header", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;
    @Schema(description = "Token expiration time in seconds", example = "86400")
    private long expiresIn;
}
