package com.payment.payment_service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credentials used to authenticate with email or document")
public class LoginRequest {
    
    @NotBlank
    @Schema(description = "Email address or raw document value", example = "user@example.com")
    private String identifier;
    
    @NotBlank
    @Schema(description = "Plain-text account password", example = "Password123!")
    private String password;
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
