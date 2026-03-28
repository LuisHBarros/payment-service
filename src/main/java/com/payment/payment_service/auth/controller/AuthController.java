package com.payment.payment_service.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.auth.dto.LoginRequest;
import com.payment.payment_service.auth.dto.LoginResponse;
import com.payment.payment_service.auth.service.AuthService;
import com.payment.payment_service.auth.service.JwtService;
import com.payment.payment_service.user.entity.UserEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        UserEntity user = authService.authenticate(request.getIdentifier(), request.getPassword());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, jwtService.getExpirationSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }
}
