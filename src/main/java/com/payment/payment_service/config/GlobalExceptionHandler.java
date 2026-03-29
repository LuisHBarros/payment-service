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

import com.payment.payment_service.config.openapi.ApiErrorResponse;
import com.payment.payment_service.transaction.exception.TransactionNotFoundException;
import com.payment.payment_service.transfer.exception.TransferException;
import com.payment.payment_service.transfer.exception.TransferNotFoundException;
import com.payment.payment_service.transfer.exception.UnauthorizedTransferException;
import com.payment.payment_service.user.exception.UserDocumentException;
import com.payment.payment_service.user.exception.UserEmailException;
import com.payment.payment_service.user.exception.UserNotFoundException;
import com.payment.payment_service.user.exception.UserPasswordException;
import com.payment.payment_service.wallet.exception.InsufficientBalanceException;
import com.payment.payment_service.wallet.exception.InvalidPaymentProviderException;
import com.payment.payment_service.wallet.exception.PaymentProviderException;
import com.payment.payment_service.wallet.exception.WalletAlreadyExistsException;
import com.payment.payment_service.wallet.exception.WalletNotFoundException;


import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(UserNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(WalletNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TransferNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TransactionNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }
    @ExceptionHandler(UserEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(UserEmailException e) {
        return build(HttpStatus.CONFLICT, e);
    }
    @ExceptionHandler(UserDocumentException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(UserDocumentException e) {
        return build(HttpStatus.CONFLICT, e);
    }
    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(WalletAlreadyExistsException e) {
        return build(HttpStatus.CONFLICT, e);
    }
    @ExceptionHandler(UnauthorizedTransferException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(UnauthorizedTransferException e) {
        return build(HttpStatus.FORBIDDEN, e);
    }
    @ExceptionHandler(UserPasswordException.class)
    public ResponseEntity<ApiErrorResponse> handleUnprocessableEntity(UserPasswordException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiErrorResponse> handleUnprocessableEntity(InsufficientBalanceException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }
    @ExceptionHandler(TransferException.class)
    public ResponseEntity<ApiErrorResponse> handleUnprocessableEntity(TransferException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

        @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(InvalidPaymentProviderException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentProviderException(PaymentProviderException e) {
        return build(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = bindingResult.getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, Exception e) {
        return build(status, e.getMessage());
    }
    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status))
            .body(new ApiErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

}
