package com.payment.payment_service.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.transaction.type.TransactionType;

public record TransactionResponseDTO(
    UUID id,
    UUID walletId,
    UUID transferId,
    TransactionType type,
    BigDecimal amount,
    LocalDateTime createdAt
) {}
