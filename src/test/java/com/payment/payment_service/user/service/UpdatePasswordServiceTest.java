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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exception.UserNotFoundException;
import com.payment.payment_service.user.exception.UserPasswordException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UpdatePasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UpdatePasswordService updatePasswordService;

    private UserEntity testUser;
    private UUID testUserId;
    private final String currentPassword = "CurrentPass123!";
    private final String currentEncodedPassword = "encodedCurrentPass123";
    private final String newPassword = "NewPass456!";
    private final String newEncodedPassword = "encodedNewPass456";

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new UserEntity();
        testUser.setId(testUserId);
        testUser.setName("John Doe");
        testUser.setEmail(new Email("john.doe@example.com"));
        testUser.setPassword(currentEncodedPassword);
        testUser.setType(UserType.COMMON);
        testUser.setActive(true);
        testUser.setDocument(new Document("52998224725")); // Valid CPF
        testUser.setDocumentHash("hash123");
        reset(userRepository, passwordEncoder);
    }

    @Test
    void execute_WithValidNewPassword_ShouldUpdatePassword() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(newEncodedPassword);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserEntity result = updatePasswordService.execute(testUserId, newPassword);

        // Assert
        assertNotNull(result);
        assertEquals(newEncodedPassword, result.getPassword());
        verify(userRepository).findById(testUserId);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void execute_WithNonExistingUser_ShouldThrowUserNotFoundException() {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        when(userRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
            updatePasswordService.execute(nonExistingId, newPassword)
        );
        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findById(nonExistingId);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_WithSameAsCurrentPassword_ShouldThrowUserPasswordException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // Act & Assert
        UserPasswordException exception = assertThrows(UserPasswordException.class, () ->
            updatePasswordService.execute(testUserId, currentPassword)
        );
        assertEquals("New password cannot be the same as the current password", exception.getMessage());
        verify(userRepository).findById(testUserId);
        verify(passwordEncoder).matches(currentPassword, currentEncodedPassword);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void execute_WithUserHavingNullPassword_ShouldSetNewPassword() {
        // Arrange
        testUser.setPassword(null);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn(newEncodedPassword);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserEntity result = updatePasswordService.execute(testUserId, newPassword);

        // Assert
        assertNotNull(result);
        assertEquals(newEncodedPassword, result.getPassword());
        verify(passwordEncoder).encode(newPassword);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void execute_ShouldReturnUpdatedUserWithSameOtherFields() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(newEncodedPassword);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserEntity result = updatePasswordService.execute(testUserId, newPassword);

        // Assert
        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals("John Doe", result.getName());
        assertEquals("john.doe@example.com", result.getEmail().value());
        assertEquals(newEncodedPassword, result.getPassword());
        assertEquals(UserType.COMMON, result.getType());
        assertTrue(result.isActive());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void execute_ShouldEncodePasswordBeforeSaving() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn(newEncodedPassword);
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // Act
        updatePasswordService.execute(testUserId, newPassword);

        // Assert
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(argThat(user ->
            newEncodedPassword.equals(user.getPassword())
        ));
    }

    @Test
    void execute_WithPasswordChange_ShouldCallRepositorySave() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(newEncodedPassword);

        // Act
        updatePasswordService.execute(testUserId, newPassword);

        // Assert
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }
}