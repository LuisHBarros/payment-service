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
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.shared.type.TransferType;
import com.payment.payment_service.transfer.dto.CreateTransferRequestDTO;
import com.payment.payment_service.transfer.dto.TransferFilterDTO;
import com.payment.payment_service.transfer.dto.TransferResponseDTO;
import com.payment.payment_service.transfer.entity.TransferEntity;
import com.payment.payment_service.transfer.service.CreateTransferService;
import com.payment.payment_service.transfer.service.GetTransferService;
import org.springframework.data.domain.Sort;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {
    private final CreateTransferService createTransferService;
    private final GetTransferService getTransferService;
    private final WalletQueryService walletQueryService;
    

    @PostMapping
    @PreAuthorize("hasRole('COMMON') or hasRole('ADMIN')")
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
    public ResponseEntity<Page<TransferResponseDTO>> findByWalletId(
        @RequestParam UUID walletId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal AuthenticatedUser auth,
        TransferFilterDTO filter) {
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
