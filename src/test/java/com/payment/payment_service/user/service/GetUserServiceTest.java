package com.payment.payment_service.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.exception.UserNotFoundException;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GetUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserService getUserService;

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
    void findById_WithExistingUser_ShouldReturnUser() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        UserEntity result = getUserService.findById(testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals("John Doe", result.getName());
        verify(userRepository).findById(testUserId);
    }

    @Test
    void findById_WithNonExistingUser_ShouldThrowUserNotFoundException() {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        when(userRepository.findById(nonExistingId)).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
            getUserService.findById(nonExistingId)
        );
        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findById(nonExistingId);
    }

    @Test
    void findAll_WithUsersInDatabase_ShouldReturnAllUsers() {
        // Arrange
        UserEntity user2 = new UserEntity();
        user2.setId(UUID.randomUUID());
        user2.setName("Jane Smith");
        user2.setEmail(new Email("jane.smith@example.com"));
        user2.setPassword("encodedPassword2");
        user2.setType(UserType.MERCHANT);
        user2.setActive(true);
        user2.setDocument(new Document("04252011000110")); // Valid CNPJ
        user2.setDocumentHash("hash456");

        List<UserEntity> expectedUsers = List.of(testUser, user2);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(expectedUsers));

        // Act
        Pageable pageable = Pageable.unpaged();
        Page<UserEntity> result = getUserService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getNumberOfElements());
        assertEquals("John Doe", result.getContent().get(0).getName());
        assertEquals("Jane Smith", result.getContent().get(1).getName());
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    void findAll_WithEmptyDatabase_ShouldReturnEmptyList() {
        // Arrange
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // Act
        Pageable pageable = Pageable.unpaged();
        Page<UserEntity> result = getUserService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    void findById_ShouldReturnCompleteUserWithAllFields() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // Act
        UserEntity result = getUserService.findById(testUserId);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertNotNull(result.getName());
        assertNotNull(result.getEmail());
        assertNotNull(result.getPassword());
        assertNotNull(result.getType());
        assertNotNull(result.isActive());
        assertNotNull(result.getDocument());
        assertNotNull(result.getDocumentHash());
        verify(userRepository).findById(testUserId);
    }
}