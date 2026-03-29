package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.payment.payment_service.wallet.type.DepositStatus;
import com.payment.payment_service.wallet.type.PaymentProviderName;

public record DepositListDTO(
    UUID id,
    UUID userId,
    UUID walletId,
    String externalPaymentReference,
    BigDecimal amount,
    DepositStatus status,
    PaymentProviderName paymentProvider,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
