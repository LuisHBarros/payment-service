package com.payment.payment_service.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.payment.payment_service.wallet.type.DepositStatus;

public record DepositResponseDTO(
    UUID depositId,
    String clientSecret,
    DepositStatus status,
    BigDecimal amount
) {
}
