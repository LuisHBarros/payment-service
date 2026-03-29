package com.payment.payment_service.transfer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.payment.payment_service.shared.dto.UserSummary;
import com.payment.payment_service.shared.dto.WalletSummary;
import com.payment.payment_service.shared.query.UserQueryService;
import com.payment.payment_service.shared.query.WalletQueryService;
import com.payment.payment_service.transfer.exception.UnauthorizedTransferException;
import com.payment.payment_service.user.type.UserType;

@ExtendWith(MockitoExtension.class)
public class TransferAuthorizationServiceTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private WalletQueryService walletQueryService;

    @InjectMocks
    private TransferAuthorizationService transferAuthorizationService;

    private final UUID sourceWalletId = UUID.randomUUID();
    private final UUID destinationWalletId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("100.00");

    @BeforeEach
    public void setUp() {
        reset(userQueryService, walletQueryService);
    }

    @Test
    void shouldAuthorizeTransferSuccessfully() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        WalletSummary sourceWallet = new WalletSummary(sourceWalletId, senderId, new BigDecimal("100.00"));
        WalletSummary destinationWallet = new WalletSummary(destinationWalletId, receiverId, new BigDecimal("50.00"));
        UserSummary sender = new UserSummary(senderId, UserType.COMMON, true);
        UserSummary receiver = new UserSummary(receiverId, UserType.MERCHANT, true);

        when(walletQueryService.getSummary(Objects.requireNonNull(sourceWalletId))).thenReturn(sourceWallet);
        when(walletQueryService.getSummary(Objects.requireNonNull(destinationWalletId))).thenReturn(destinationWallet);
        when(userQueryService.getSummary(senderId)).thenReturn(sender);
        when(userQueryService.getSummary(receiverId)).thenReturn(receiver);

        assertDoesNotThrow(() ->
            transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount)
        );
    }

    @Test
    void shouldThrowWhenSenderAndReceiverAreSameUser() {
        UUID userId = UUID.randomUUID();

        WalletSummary sourceWallet = new WalletSummary(sourceWalletId, userId, new BigDecimal("100.00"));
        WalletSummary destinationWallet = new WalletSummary(destinationWalletId, userId, new BigDecimal("50.00"));
        UserSummary user = new UserSummary(userId, UserType.COMMON, true);

        when(walletQueryService.getSummary(Objects.requireNonNull(sourceWalletId))).thenReturn(sourceWallet);
        when(walletQueryService.getSummary(Objects.requireNonNull(destinationWalletId))).thenReturn(destinationWallet);
        when(userQueryService.getSummary(userId)).thenReturn(user);

        assertThrows(UnauthorizedTransferException.class, () ->
            transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount)
        );
    }

    @Test
    void shouldThrowWhenSenderCannotSend() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        WalletSummary sourceWallet = new WalletSummary(sourceWalletId, senderId, new BigDecimal("100.00"));
        WalletSummary destinationWallet = new WalletSummary(destinationWalletId, receiverId, new BigDecimal("50.00"));
        UserSummary sender = new UserSummary(senderId, UserType.MERCHANT, true);
        UserSummary receiver = new UserSummary(receiverId, UserType.MERCHANT, true);

        when(walletQueryService.getSummary(Objects.requireNonNull(sourceWalletId))).thenReturn(sourceWallet);
        when(walletQueryService.getSummary(Objects.requireNonNull(destinationWalletId))).thenReturn(destinationWallet);
        when(userQueryService.getSummary(senderId)).thenReturn(sender);
        when(userQueryService.getSummary(receiverId)).thenReturn(receiver);

        assertThrows(AccessDeniedException.class, () ->
            transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount)
        );
    }

    @Test
    void shouldThrowWhenReceiverCannotReceive() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        WalletSummary sourceWallet = new WalletSummary(sourceWalletId, senderId, new BigDecimal("100.00"));
        WalletSummary destinationWallet = new WalletSummary(destinationWalletId, receiverId, new BigDecimal("50.00"));
        UserSummary sender = new UserSummary(senderId, UserType.COMMON, true);
        UserSummary receiver = new UserSummary(receiverId, UserType.COMMON, true);

        when(walletQueryService.getSummary(Objects.requireNonNull(sourceWalletId))).thenReturn(sourceWallet);
        when(walletQueryService.getSummary(Objects.requireNonNull(destinationWalletId))).thenReturn(destinationWallet);
        when(userQueryService.getSummary(senderId)).thenReturn(sender);
        when(userQueryService.getSummary(receiverId)).thenReturn(receiver);

        assertThrows(UnauthorizedTransferException.class, () ->
            transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount)
        );
    }

    @Test
    void shouldThrowWhenBalanceIsInsufficient() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        BigDecimal insufficientBalance = new BigDecimal("5.00");

        WalletSummary sourceWallet = new WalletSummary(sourceWalletId, senderId, insufficientBalance);
        WalletSummary destinationWallet = new WalletSummary(destinationWalletId, receiverId, new BigDecimal("50.00"));
        UserSummary sender = new UserSummary(senderId, UserType.COMMON, true);
        UserSummary receiver = new UserSummary(receiverId, UserType.MERCHANT, true);

        when(walletQueryService.getSummary(Objects.requireNonNull(sourceWalletId))).thenReturn(sourceWallet);
        when(walletQueryService.getSummary(Objects.requireNonNull(destinationWalletId))).thenReturn(destinationWallet);
        when(userQueryService.getSummary(senderId)).thenReturn(sender);
        when(userQueryService.getSummary(receiverId)).thenReturn(receiver);

        assertThrows(UnauthorizedTransferException.class, () ->
            transferAuthorizationService.authorize(sourceWalletId, destinationWalletId, amount)
        );
    }
}
