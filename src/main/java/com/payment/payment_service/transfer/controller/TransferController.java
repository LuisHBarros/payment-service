package com.payment.payment_service.transfer.controller;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.config.SecurityUtils;
import com.payment.payment_service.config.openapi.ApiErrorResponse;
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.shared.type.TransferType;
import com.payment.payment_service.transfer.dto.CreateTransferRequestDTO;
import com.payment.payment_service.transfer.dto.TransferFilterDTO;
import com.payment.payment_service.transfer.dto.TransferResponseDTO;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.service.CreateTransferService;
import com.payment.payment_service.transfer.service.GetTransferService;
import org.springframework.data.domain.Sort;

import org.springdoc.core.annotations.ParameterObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "transfers", description = "Transfer creation and search endpoints")
public class TransferController {
    private final CreateTransferService createTransferService;
    private final GetTransferService getTransferService;
    private final WalletQueryService walletQueryService;
    

    @PostMapping
    @PreAuthorize("hasRole('COMMON') or hasRole('ADMIN')")
    @Operation(summary = "Create transfer", description = "Creates a transfer between two wallets.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Caller is not allowed to use the source wallet",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Transfer business validation failed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    public ResponseEntity<TransferResponseDTO> createTransfer(@RequestBody @Valid CreateTransferRequestDTO request,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        SecurityUtils.requireOwnership(auth, walletQueryService.getSummary(Objects.requireNonNull(request.sourceWalletId())).userId());
        UUID transferId = createTransferService.execute(
            request.sourceWalletId(), request.destinationWalletId(), request.amount()
        );
        TransferEntity transfer = getTransferService.execute(transferId);
        TransferResponseDTO response = toResponseDTO(transfer);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "List transfers by wallet", description = "Returns transfers for a wallet using optional filters.")
    public ResponseEntity<Page<TransferResponseDTO>> findByWalletId(
        @RequestParam UUID walletId,
        @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal AuthenticatedUser auth,
        @ParameterObject TransferFilterDTO filter) {
        SecurityUtils.requireOwnership(auth, walletQueryService.getSummary(Objects.requireNonNull(walletId)).userId());
        Page<TransferResponseDTO> response = getTransferService
            .findByWalletId(walletId, pageable, filter)
            .map(transfer -> toResponseDTO(transfer, walletId));
        return ResponseEntity.ok(response);
    }


    private TransferResponseDTO toResponseDTO(TransferEntity transfer) {
        return toResponseDTO(transfer, null);
    }

    private TransferResponseDTO toResponseDTO(TransferEntity transfer, UUID walletId) {
        TransferType type = null;
        if (walletId != null) {
            type = walletId.equals(transfer.getSourceWalletId()) ? TransferType.DEBIT : TransferType.CREDIT;
        }
        return new TransferResponseDTO(
            transfer.getId(),
            transfer.getSourceWalletId(),
            transfer.getDestinationWalletId(),
            transfer.getAmount(),
            transfer.getStatus(),
            transfer.getCreatedAt(),
            transfer.getUpdatedAt(),
            type
        );
    }


}
