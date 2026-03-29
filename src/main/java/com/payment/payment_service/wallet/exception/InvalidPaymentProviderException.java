package com.payment.payment_service.wallet.exception;

public class InvalidPaymentProviderException extends RuntimeException {
    public InvalidPaymentProviderException(String message) {
        super(message);
    }
}
