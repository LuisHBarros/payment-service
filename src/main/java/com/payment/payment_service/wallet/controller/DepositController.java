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
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.config.SecurityUtils;
import com.payment.payment_service.config.openapi.ApiErrorResponse;
import com.payment.payment_service.wallet.dto.CreateDepositRequestDTO;
import com.payment.payment_service.wallet.dto.DepositListDTO;
import com.payment.payment_service.wallet.dto.DepositResponseDTO;
import com.payment.payment_service.wallet.exception.WebhookSignatureException;
import com.payment.payment_service.wallet.provider.PaymentProvider;
import com.payment.payment_service.wallet.provider.WebhookResult;
import com.payment.payment_service.wallet.service.CreateDepositService;
import com.payment.payment_service.wallet.service.GetDepositsService;
import com.payment.payment_service.wallet.service.ProcessDepositService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "deposits", description = "Deposit creation, listing and webhook endpoints")
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
    @Operation(summary = "Create deposit", description = "Creates a deposit intent for the wallet owner.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Deposit created successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Caller is not allowed to access the wallet",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Deposit validation failed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "502",
            description = "Payment provider unavailable",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    public ResponseEntity<DepositResponseDTO> createDeposit(
        @PathVariable UUID userId,
        @org.springframework.web.bind.annotation.RequestBody @Valid CreateDepositRequestDTO request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        SecurityUtils.requireOwnership(authenticatedUser, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            createDepositService.execute(authenticatedUser.userId(), request)
        );
    }

    @GetMapping("/api/v1/wallets/{userId}/deposits")
    @Operation(summary = "List deposits", description = "Returns all deposits for the wallet owner.")
    public ResponseEntity<List<DepositListDTO>> getDeposits(
        @PathVariable UUID userId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        SecurityUtils.requireOwnership(authenticatedUser, userId);
        return ResponseEntity.ok(
            getDepositsService.execute(authenticatedUser.userId())
        );
    }

    @PostMapping("/api/v1/webhooks/deposits")
    @Operation(
        summary = "Receive deposit webhook",
        description = "Receives payment-provider webhook notifications for deposit state changes."
    )
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook processed or intentionally ignored"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook payload"),
        @ApiResponse(responseCode = "401", description = "Invalid webhook signature"),
        @ApiResponse(responseCode = "500", description = "Webhook processing failed")
    })
    public ResponseEntity<Void> handleWebhook(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Raw webhook payload sent by the configured payment provider.",
                required = true,
                content = @Content(schema = @Schema(type = "string"))
            )
            @org.springframework.web.bind.annotation.RequestBody String payload,
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
