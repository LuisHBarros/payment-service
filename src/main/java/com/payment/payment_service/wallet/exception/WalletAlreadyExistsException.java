package com.payment.payment_service.wallet.exception;

public class WalletAlreadyExistsException extends RuntimeException {
    
    public WalletAlreadyExistsException(String message) {
        super(message);
    }
    
}
