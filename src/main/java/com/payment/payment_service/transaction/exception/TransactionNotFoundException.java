package com.payment.payment_service.transaction.exception;

public class TransactionNotFoundException extends RuntimeException {
    
    public TransactionNotFoundException(String message) {
        super(message);
    }
    
}
