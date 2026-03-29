package com.payment.payment_service.wallet.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.config.SecurityUtils;
import com.payment.payment_service.wallet.dto.CreateDepositRequestDTO;
import com.payment.payment_service.wallet.dto.DepositListDTO;
import com.payment.payment_service.wallet.dto.DepositResponseDTO;
import com.payment.payment_service.wallet.exception.WebhookSignatureException;
import com.payment.payment_service.wallet.provider.PaymentProvider;
import com.payment.payment_service.wallet.provider.WebhookResult;
import com.payment.payment_service.wallet.service.CreateDepositService;
import com.payment.payment_service.wallet.service.GetDepositsService;
import com.payment.payment_service.wallet.service.ProcessDepositService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DepositController {

    private final CreateDepositService createDepositService;
    private final ProcessDepositService processDepositService;
    private final GetDepositsService getDepositsService;

    @Value("${payment.provider}")
    private String paymentProvider;

    @Value("${payment.webhook_secret}")
    private String webhookSecret;

    @Value("${payment.webhook_header_name}")
    private String webhookHeaderName;

    private final Map<String, PaymentProvider> paymentProviders;    

    @PostMapping("/api/v1/wallets/{userId}/deposits")
    public ResponseEntity<DepositResponseDTO> createDeposit(
        @PathVariable UUID userId,
        @RequestBody @Valid CreateDepositRequestDTO request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        SecurityUtils.requireOwnership(authenticatedUser, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            createDepositService.execute(authenticatedUser.userId(), request)
        );
    }

    @GetMapping("/api/v1/wallets/{userId}/deposits")
    public ResponseEntity<List<DepositListDTO>> getDeposits(
        @PathVariable UUID userId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        SecurityUtils.requireOwnership(authenticatedUser, userId);
        return ResponseEntity.ok(
            getDepositsService.execute(authenticatedUser.userId())
        );
    }

    @PostMapping("/api/v1/webhooks/deposits")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            HttpServletRequest request) {

        String signature = request.getHeader(webhookHeaderName);
        PaymentProvider provider = paymentProviders.get(paymentProvider);

        Optional<WebhookResult> result;
        try {
            result = provider.parseWebhookEvent(payload, signature, webhookSecret);
        } catch (WebhookSignatureException e) {
            log.warn("Invalid webhook signature from provider={}", paymentProvider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        catch (Exception e) {
            log.error("Error parsing webhook event", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (result.isEmpty()) {
            log.info("Ignoring unsupported event from provider={}", paymentProvider);
            return ResponseEntity.ok().build();
        }

        try {
            processDepositService.execute(
                result.get().externalReference(),
                paymentProvider,
                result.get().status()
            );
        } catch (Exception e) {
            log.error("Failed to process webhook event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok().build();
    }
    
    
}
