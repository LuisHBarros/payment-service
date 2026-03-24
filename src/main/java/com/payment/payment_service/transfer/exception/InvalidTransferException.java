package com.payment.payment_service.transfer.exception;

public class InvalidTransferException extends RuntimeException {
    
    public InvalidTransferException(String message) {
        super(message);
    }
}
