package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.type.PaymentProviderName;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DepositListItem", description = "Deposit item returned by the deposit listing endpoint")
public record DepositListDTO(
    @Schema(description = "Deposit identifier")
    UUID id,
    @Schema(description = "Owner user identifier")
    UUID userId,
    @Schema(description = "Wallet identifier")
    UUID walletId,
    @Schema(description = "External payment reference")
    String externalPaymentReference,
    @Schema(description = "Deposit amount")
    BigDecimal amount,
    @Schema(description = "Deposit status")
    DepositStatus status,
    @Schema(description = "Payment provider used by the deposit")
    PaymentProviderName paymentProvider,
    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt,
    @Schema(description = "Last update timestamp")
    LocalDateTime updatedAt
) {
}
