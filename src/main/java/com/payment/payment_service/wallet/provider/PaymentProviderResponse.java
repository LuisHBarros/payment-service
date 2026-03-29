package com.payment.payment_service.wallet.provider;

import com.payment.payment_service.wallet.type.DepositStatus;

public record PaymentProviderResponse(
    String clientSecret,
    String externalPaymentReference,
    DepositStatus status,
    String rawResponse
) {
    
}
