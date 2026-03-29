package com.payment.payment_service.wallet.controller;

import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.config.SecurityUtils;
import com.payment.payment_service.config.openapi.ApiErrorResponse;
import com.payment.payment_service.wallet.dto.WalletResponseDTO;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.service.GetWalletService;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "wallets", description = "Wallet read endpoints")
public class WalletController {
    private final GetWalletService getWalletService;
    
    public WalletController(GetWalletService getWalletService) {
        this.getWalletService = getWalletService;
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "Get wallet by user id", description = "Returns the wallet owned by the provided user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wallet returned successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Caller is not allowed to access the wallet",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Wallet not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    public ResponseEntity<WalletResponseDTO> getWallet(@PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        SecurityUtils.requireOwnership(auth, userId);
        WalletEntity wallet = getWalletService.execute(userId);
        return ResponseEntity.ok(toResponseDTO(wallet));
    }



    private WalletResponseDTO toResponseDTO(WalletEntity wallet) {
        return new WalletResponseDTO(
            wallet.getId(),
            wallet.getUserId(),
            wallet.getBalance(),
            wallet.getCreatedAt(),
            wallet.getUpdatedAt()
        );
    }
}

    
