package com.payment.payment_service.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.auth.dto.LoginRequest;
import com.payment.payment_service.auth.dto.LoginResponse;
import com.payment.payment_service.auth.service.AuthService;
import com.payment.payment_service.auth.service.JwtService;
import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.user.dto.UserResponseDTO;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.service.GetUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final GetUserService getUserService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        UserEntity user = authService.authenticate(request.getIdentifier(), request.getPassword());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, jwtService.getExpirationSeconds()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(@AuthenticationPrincipal AuthenticatedUser auth) {
        UserEntity user = getUserService.findById(auth.userId());
        return ResponseEntity.ok(toResponseDTO(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }

    private UserResponseDTO toResponseDTO(UserEntity user) {
        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail().value(),
            user.getDocument().masked(),
            user.getType(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
