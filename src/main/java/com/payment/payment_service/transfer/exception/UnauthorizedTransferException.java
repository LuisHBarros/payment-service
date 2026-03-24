package com.payment.payment_service.transfer.exception;

public class UnauthorizedTransferException extends RuntimeException {
    
    public UnauthorizedTransferException(String message) {
        super(message);
    }
}
