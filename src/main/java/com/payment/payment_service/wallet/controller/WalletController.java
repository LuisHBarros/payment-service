package com.payment.payment_service.wallet.controller;

import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.wallet.dto.WalletResponseDTO;
import com.payment.payment_service.wallet.entity.WalletEntity;
import com.payment.payment_service.wallet.service.GetWalletService;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final GetWalletService getWalletService;
    
    public WalletController(GetWalletService getWalletService) {
        this.getWalletService = getWalletService;
    }
    
    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponseDTO> getWallet(@PathVariable UUID userId) {
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

    
