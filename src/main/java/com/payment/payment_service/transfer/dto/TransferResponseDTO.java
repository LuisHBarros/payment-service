package com.payment.payment_service.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.shared.type.TransferType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransferResponse", description = "Transfer returned by the API")
public record TransferResponseDTO(
    @Schema(description = "Transfer identifier")
    UUID id,
    @Schema(description = "Source wallet identifier")
    UUID sourceWalletId,
    @Schema(description = "Destination wallet identifier")
    UUID destinationWalletId,
    @Schema(description = "Transfer amount")
    BigDecimal amount,
    @Schema(description = "Current transfer status")
    TransferStatus status,
    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt,
    @Schema(description = "Last update timestamp")
    LocalDateTime updatedAt,
    @Schema(description = "Transfer type relative to the wallet used in the query")
    TransferType type
) {}
