package com.payment.payment_service.shared.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletSummary(
    UUID id,
    UUID userId,
    BigDecimal balance
) {
    
}
