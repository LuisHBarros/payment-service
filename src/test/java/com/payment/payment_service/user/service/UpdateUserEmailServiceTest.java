package com.payment.payment_service.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import com.payment.payment_service.user.exceptions.UserEmailException;
import com.payment.payment_service.user.exceptions.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@ExtendWith(MockitoExtension.class)
class UpdateUserEmailServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UpdateUserEmailService updateUserEmailService;

    private UserEntity testUser;
    private UUID testUserId;
    private final String currentEmail = "current.email@example.com";
    private final String newEmail = "new.email@example.com";

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new UserEntity();
        testUser.setId(testUserId);
        testUser.setName("John Doe");
        testUser.setEmail(new Email(currentEmail));
        testUser.setPassword("encodedPassword");
        testUser.setType(UserType.COMMON);
        testUser.setActive(true);
        testUser.setDocument(new Document("52998224725")); // Valid CPF
        testUser.setDocumentHash("hash123");
        reset(userRepository);
    }

    @Test
    void execute_WithValidNewEmail_ShouldUpdateUserEmail() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserEntity result = updateUserEmailService.execute(testUserId, newEmail);

        // Assert
        assertNotNull(result);
        assertEquals(newEmail, result.getEmail().value());
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void execute_WithNonExistingUser_ShouldThrowUserNotFoundException() {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        when(userRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
            updateUserEmailService.execute(nonExistingId, newEmail)
        );
        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findById(nonExistingId);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_WithSameEmail_ShouldThrowUserEmailException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        UserEmailException exception = assertThrows(UserEmailException.class, () ->
            updateUserEmailService.execute(testUserId, currentEmail)
        );
        assertEquals("Email is the same", exception.getMessage());
        verify(userRepository).findById(testUserId);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_WithNullEmail_ShouldThrowNullPointerException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            updateUserEmailService.execute(testUserId, null)
        );
        verify(userRepository).findById(testUserId);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_ShouldReturnUpdatedUserWithSameOtherFields() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserEntity result = updateUserEmailService.execute(testUserId, newEmail);

        // Assert
        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals("John Doe", result.getName());
        assertEquals(newEmail, result.getEmail().value());
        assertEquals("encodedPassword", result.getPassword());
        assertEquals(UserType.COMMON, result.getType());
        assertTrue(result.getActive());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void execute_WithEmailChange_ShouldCallRepositorySave() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // Act
        updateUserEmailService.execute(testUserId, newEmail);

        // Assert
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }
}