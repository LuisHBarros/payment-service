package com.payment.payment_service.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DeleteUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeleteUserService deleteUserService;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new UserEntity();
        testUser.setId(testUserId);
        testUser.setName("John Doe");
        testUser.setEmail(new Email("john.doe@example.com"));
        testUser.setPassword("encodedPassword");
        testUser.setType(UserType.COMMON);
        testUser.setActive(true);
        testUser.setDocument(new Document("52998224725")); // Valid CPF
        testUser.setDocumentHash("hash123");
        reset(userRepository);
    }

    @Test
    void execute_WithExistingUser_ShouldSoftDeleteUser() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        deleteUserService.execute(testUserId);

        verify(userRepository).findById(testUserId);
        verify(userRepository).save(argThat(user -> !user.isActive()));
    }

    @Test
    void execute_WithNonExistingUser_ShouldThrowUserNotFoundException() {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        when(userRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
            deleteUserService.execute(nonExistingId)
        );
        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findById(nonExistingId);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_ShouldSetActiveToFalse() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, UserEntity.class));

        // Act
        deleteUserService.execute(testUserId);

        // Assert
        verify(userRepository).save(argThat(user -> !user.isActive()));
    }

    @Test
    void execute_ShouldPreserveOtherUserFields() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, UserEntity.class));

        // Act
        deleteUserService.execute(testUserId);

        // Assert
        verify(userRepository).save(argThat(user ->
            user != null &&
            testUserId.equals(user.getId()) &&
            "John Doe".equals(user.getName()) &&
            "john.doe@example.com".equals(user.getEmail().value()) &&
            "encodedPassword".equals(user.getPassword()) &&
            UserType.COMMON.equals(user.getType()) &&
            "52998224725".equals(user.getDocument().value()) &&
            "hash123".equals(user.getDocumentHash())
        ));
    }

    @Test
    void execute_ShouldCallRepositorySaveExactlyOnce() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // Act
        deleteUserService.execute(testUserId);

        // Assert
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void execute_WithAlreadyInactiveUser_ShouldStillUpdateUser() {
        // Arrange
        testUser.setActive(false);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0, UserEntity.class));

        // Act
        deleteUserService.execute(testUserId);

        // Assert
        verify(userRepository).save(argThat(user -> !user.isActive()));
    }

    @Test
    void execute_VerifyNoReturn() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // Act & Assert
        assertDoesNotThrow(() -> {
            deleteUserService.execute(testUserId);
        });
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(any(UserEntity.class));
    }
}