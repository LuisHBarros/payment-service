package com.payment.payment_service.shared.query;

import java.util.UUID;
import com.payment.payment_service.shared.dto.WalletSummary;

public interface WalletQueryService {
    WalletSummary getSummary(UUID id);
}