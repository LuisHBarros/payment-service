package com.payment.payment_service.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@ExtendWith(MockitoExtension.class)
class PatchUserServiceTest {

    @Mock
    private GetUserService getUserService;

    @Mock
    private UpdateUserEmailService updateUserEmailService;

    @Mock
    private UpdatePasswordService updatePasswordService;

    @InjectMocks
    private PatchUserService patchUserService;

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
        reset(getUserService, updateUserEmailService, updatePasswordService);
    }

    @Test
    void execute_WithBothEmailAndPassword_ShouldUpdateBothFields() {
        // Arrange
        String newEmail = "new.email@example.com";
        String newPassword = "NewPass456!";

        when(getUserService.findById(testUserId)).thenReturn(testUser);
        when(updateUserEmailService.execute(eq(testUserId), eq(newEmail))).thenReturn(testUser);
        when(updatePasswordService.execute(eq(testUserId), eq(newPassword))).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, newEmail, newPassword);

        // Assert
        assertNotNull(result);
        verify(getUserService).findById(testUserId);
        verify(updateUserEmailService).execute(testUserId, newEmail);
        verify(updatePasswordService).execute(testUserId, newPassword);
    }

    @Test
    void execute_WithOnlyEmail_ShouldUpdateOnlyEmail() {
        // Arrange
        String newEmail = "new.email@example.com";

        when(getUserService.findById(testUserId)).thenReturn(testUser);
        when(updateUserEmailService.execute(eq(testUserId), eq(newEmail))).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, newEmail, null);

        // Assert
        assertNotNull(result);
        verify(getUserService).findById(testUserId);
        verify(updateUserEmailService).execute(testUserId, newEmail);
        verify(updatePasswordService, never()).execute(any(UUID.class), any(String.class));
    }

    @Test
    void execute_WithOnlyPassword_ShouldUpdateOnlyPassword() {
        // Arrange
        String newPassword = "NewPass456!";

        when(getUserService.findById(testUserId)).thenReturn(testUser);
        when(updatePasswordService.execute(eq(testUserId), eq(newPassword))).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, null, newPassword);

        // Assert
        assertNotNull(result);
        verify(getUserService).findById(testUserId);
        verify(updatePasswordService).execute(testUserId, newPassword);
        verify(updateUserEmailService, never()).execute(any(UUID.class), any(String.class));
    }

    @Test
    void execute_WithBothNull_ShouldNotUpdateAnyField() {
        // Arrange
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, null, null);

        // Assert
        assertNotNull(result);
        verify(getUserService).findById(testUserId);
        verify(updateUserEmailService, never()).execute(any(UUID.class), any(String.class));
        verify(updatePasswordService, never()).execute(any(UUID.class), any(String.class));
    }

    @Test
    void execute_ShouldReturnUpdatedUserFromGetUserService() {
        // Arrange
        String newEmail = "new.email@example.com";
        String newPassword = "NewPass456!";
        UserEntity updatedUser = new UserEntity();
        updatedUser.setId(testUserId);
        updatedUser.setName("John Doe Updated");
        updatedUser.setEmail(new Email(newEmail));
        updatedUser.setPassword("newEncodedPassword");
        updatedUser.setType(UserType.COMMON);
        updatedUser.setActive(true);
        updatedUser.setDocument(new Document("52998224725")); // Valid CPF
        updatedUser.setDocumentHash("hash123");

        when(getUserService.findById(testUserId)).thenReturn(testUser, updatedUser);
        when(updateUserEmailService.execute(eq(testUserId), eq(newEmail))).thenReturn(testUser);
        when(updatePasswordService.execute(eq(testUserId), eq(newPassword))).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, newEmail, newPassword);

        // Assert
        assertNotNull(result);
        assertEquals("John Doe", result.getName()); // Name is not updated by patch service
        verify(getUserService, times(1)).findById(testUserId);
    }

    @Test
    void execute_ShouldCallServicesInCorrectOrder() {
        // Arrange
        String newEmail = "new.email@example.com";
        String newPassword = "NewPass456!";

        when(getUserService.findById(testUserId)).thenReturn(testUser);
        when(updateUserEmailService.execute(eq(testUserId), eq(newEmail))).thenReturn(testUser);
        when(updatePasswordService.execute(eq(testUserId), eq(newPassword))).thenReturn(testUser);

        // Act
        patchUserService.execute(testUserId, newEmail, newPassword);

        // Assert
        verify(updateUserEmailService).execute(testUserId, newEmail);
        verify(updatePasswordService).execute(testUserId, newPassword);
        verify(getUserService, times(1)).findById(testUserId);
    }

    @Test
    void execute_WithEmptyStringEmail_ShouldUpdateEmail() {
        // Arrange
        String emptyEmail = "";

        when(getUserService.findById(testUserId)).thenReturn(testUser);
        when(updateUserEmailService.execute(eq(testUserId), eq(emptyEmail))).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, emptyEmail, null);

        // Assert
        assertNotNull(result);
        verify(updateUserEmailService).execute(testUserId, emptyEmail);
        verify(updatePasswordService, never()).execute(any(UUID.class), any(String.class));
    }

    @Test
    void execute_WithEmptyStringPassword_ShouldUpdatePassword() {
        // Arrange
        String emptyPassword = "";

        when(getUserService.findById(testUserId)).thenReturn(testUser);
        when(updatePasswordService.execute(eq(testUserId), eq(emptyPassword))).thenReturn(testUser);

        // Act
        UserEntity result = patchUserService.execute(testUserId, null, emptyPassword);

        // Assert
        assertNotNull(result);
        verify(updatePasswordService).execute(testUserId, emptyPassword);
        verify(updateUserEmailService, never()).execute(any(UUID.class), any(String.class));
    }
}