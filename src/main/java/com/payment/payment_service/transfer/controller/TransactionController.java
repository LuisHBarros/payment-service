package com.payment.payment_service.transfer.controller;

import java.util.UUID;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payment_service.transfer.dto.CreateTransferRequestDTO;
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
public class TransactionController {
    private final CreateTransferService createTransferService;
    private final GetTransferService getTransferService;
    

    @PostMapping
    public ResponseEntity<TransferResponseDTO> createTransfer(@RequestBody @Valid CreateTransferRequestDTO request) {
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
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TransferResponseDTO> response = getTransferService
            .findByWalletId(walletId, pageable)
            .map(this::toResponseDTO);
        return ResponseEntity.ok(response);
    }


    private TransferResponseDTO toResponseDTO(TransferEntity transfer) {
        return new TransferResponseDTO(
            transfer.getId(),
            transfer.getSourceWalletId(),
            transfer.getDestinationWalletId(),
            transfer.getAmount(),
            transfer.getStatus(),
            transfer.getCreatedAt(),
            transfer.getUpdatedAt()
        );
    }


}


