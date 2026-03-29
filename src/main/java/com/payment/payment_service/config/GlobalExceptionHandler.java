package com.payment.payment_service.config;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.payment.payment_service.transaction.exception.TransactionNotFoundException;
import com.payment.payment_service.transfer.exception.TransferException;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.exception.UnauthorizedTransferException;
import com.payment.payment_service.user.exceptions.UserDocumentException;
import com.payment.payment_service.user.exceptions.UserEmailException;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.exceptions.UserPasswordException;
import com.payment.payment_service.wallet.exception.InsufficientBalanceException;
import com.payment.payment_service.wallet.exception.InvalidPaymentProviderException;
import com.payment.payment_service.wallet.exception.PaymentProviderException;
import com.payment.payment_service.wallet.exception.WalletAlreadyExistsException;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;


import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    record ErrorResponse(int status, String error, String message) {}
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WalletNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TransferNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TransactionNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(UserEmailException.class)
    public ResponseEntity<ErrorResponse> handleConflict(UserEmailException e) {
        return build(HttpStatus.CONFLICT, e);
    }
    @ExceptionHandler(UserDocumentException.class)
    public ResponseEntity<ErrorResponse> handleConflict(UserDocumentException e) {
        return build(HttpStatus.CONFLICT, e);
    }
    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConflict(WalletAlreadyExistsException e) {
        return build(HttpStatus.CONFLICT, e);
    }
    @ExceptionHandler(UnauthorizedTransferException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(UnauthorizedTransferException e) {
        return build(HttpStatus.FORBIDDEN, e);
    }
    @ExceptionHandler(UserPasswordException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntity(UserPasswordException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntity(InsufficientBalanceException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }
    @ExceptionHandler(TransferException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntity(TransferException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

        @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentProviderException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(InvalidPaymentProviderException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProviderException(PaymentProviderException e) {
        return build(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = bindingResult.getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandled(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
    private ResponseEntity<ErrorResponse> build(HttpStatus status, Exception e) {
        return build(status, e.getMessage());
    }
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status))
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

}
