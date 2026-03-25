package com.payment.payment_service.transfer.listener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.payment.payment_service.transfer.event.TransferCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferCreatedListener {
    private final TransferPublishService transferPublishService;



@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(TransferCreatedEvent event) {
    transferPublishService.publish(event);
}
}
