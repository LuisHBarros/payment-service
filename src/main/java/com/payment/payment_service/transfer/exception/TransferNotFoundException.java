package com.payment.payment_service.transfer.exception;

public class TransferNotFoundException extends RuntimeException {
    
    public TransferNotFoundException(String message) {
        super(message);
    }
    
}
