package com.payment.payment_service.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.transaction.type.TransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransactionResponse", description = "Transaction returned by the API")
public record TransactionResponseDTO(
    @Schema(description = "Transaction identifier")
    UUID id,
    @Schema(description = "Wallet identifier")
    UUID walletId,
    @Schema(description = "Associated transfer identifier when applicable")
    UUID transferId,
    @Schema(description = "Transaction type")
    TransactionType type,
    @Schema(description = "Transaction amount")
    BigDecimal amount,
    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt
) {}
