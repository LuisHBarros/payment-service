package com.payment.payment_service.transfer.dto;

import java.time.LocalDate;

import com.payment.payment_service.shared.type.TransferStatus;
import com.payment.payment_service.shared.type.TransferType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransferFilter", description = "Query filters used when listing transfers")
public record TransferFilterDTO(
    @Schema(description = "Transfer status filter")
    TransferStatus status,
    @Schema(description = "Start date filter in ISO-8601 format", example = "2026-03-01")
    LocalDate startDate,
    @Schema(description = "End date filter in ISO-8601 format", example = "2026-03-31")
    LocalDate endDate,
    @Schema(description = "Transfer type relative to the informed wallet")
    TransferType type
) {
    
}
