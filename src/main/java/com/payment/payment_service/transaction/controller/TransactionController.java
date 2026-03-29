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
import com.payment.payment_service.config.openapi.ApiErrorResponse;
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.transaction.dto.TransactionResponseDTO;
import com.payment.payment_service.transaction.entity.TransactionEntity;
import com.payment.payment_service.transaction.service.GetTransactionService;
import com.payment.payment_service.transaction.type.TransactionType;

import org.springdoc.core.annotations.ParameterObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "transactions", description = "Transaction search endpoints")
public class TransactionController {

    private final GetTransactionService getTransactionService;
    private final WalletQueryService walletQueryService;

    @GetMapping
    @Operation(
        summary = "List transactions",
        description = "Returns transactions by wallet or transfer. At least one of walletId or transferId must be provided."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transactions returned successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Missing required query parameters",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Caller is not allowed to access the wallet",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    public ResponseEntity<Page<TransactionResponseDTO>> findTransactions(
            @Parameter(description = "Wallet id to search transactions for")
            @RequestParam(required = false) UUID walletId,
            @Parameter(description = "Transfer id to search transactions for")
            @RequestParam(required = false) UUID transferId,
            @Parameter(description = "Transaction type filter")
            @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Filter start timestamp in ISO-8601 format", example = "2026-03-29T00:00:00")
            @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "Filter end timestamp in ISO-8601 format", example = "2026-03-29T23:59:59")
            @RequestParam(required = false) LocalDateTime endDate,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
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
    @Operation(summary = "Get transaction by id", description = "Returns a transaction when the caller owns the related wallet.")
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
