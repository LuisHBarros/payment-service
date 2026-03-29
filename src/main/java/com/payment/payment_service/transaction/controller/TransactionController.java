package com.payment.payment_service.transaction.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.config.SecurityUtils;
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.transaction.dto.TransactionResponseDTO;
import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.service.GetTransactionService;
import com.payment.payment_service.transaction.type.TransactionType;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final GetTransactionService getTransactionService;
    private final WalletQueryService walletQueryService;

    @GetMapping
    public ResponseEntity<Page<TransactionResponseDTO>> findTransactions(
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) UUID transferId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser auth) {

        if (walletId != null) {
            SecurityUtils.requireOwnership(auth, walletQueryService.getSummary(walletId).userId());
            Page<TransactionResponseDTO> response = getTransactionService
                .findByWalletId(walletId, pageable, type, startDate, endDate)
                .map(this::toResponseDTO);
            return ResponseEntity.ok(response);
        }

        if (transferId != null) {
            Page<TransactionResponseDTO> response = getTransactionService
                .findByTransferId(transferId, pageable)
                .map(this::toResponseDTO);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        TransactionEntity transaction = getTransactionService.findById(id);
        SecurityUtils.requireOwnership(auth, walletQueryService.getSummary(transaction.getWalletId()).userId());
        return ResponseEntity.ok(toResponseDTO(transaction));
    }

    private TransactionResponseDTO toResponseDTO(TransactionEntity transaction) {
        return new TransactionResponseDTO(
            transaction.getId(),
            transaction.getWalletId(),
            transaction.getTransferId(),
            transaction.getType(),
            transaction.getAmount(),
            transaction.getCreatedAt()
        );
    }
}
