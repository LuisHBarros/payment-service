package com.payment.payment_service.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.shared.type.TransferType;

public record TransferResponseDTO(
    UUID id,
    UUID sourceWalletId,
    UUID destinationWalletId,
    BigDecimal amount,
    TransferStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    TransferType type
) {}