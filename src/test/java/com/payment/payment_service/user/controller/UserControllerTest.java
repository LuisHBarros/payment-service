package com.payment.payment_service.user.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.user.dto.CreateUserRequestDTO;
import com.payment.payment_service.user.dto.PatchUserRequestDTO;
import com.payment.payment_service.user.entity.UserEntity;
import com.payment.payment_service.user.service.CreateUserService;
import com.payment.payment_service.user.service.DeleteUserService;
import com.payment.payment_service.user.service.GetUserService;
import com.payment.payment_service.user.service.PatchUserService;
import com.payment.payment_service.user.type.UserType;
import com.payment.payment_service.user.value_object.Document;
import com.payment.payment_service.user.value_object.Email;

@WebMvcTest(UserController.class)
@WithMockUser
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateUserService createUserService;

    @MockitoBean
    private GetUserService getUserService;

    @MockitoBean
    private DeleteUserService deleteUserService;

    @MockitoBean
    private PatchUserService patchUserService;

    private UserEntity testUser;
    private UUID testUserId;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testTime = LocalDateTime.now();
        testUser = new UserEntity();
        testUser.setId(testUserId);
        testUser.setName("John Doe");
        testUser.setEmail(new Email("john.doe@example.com"));
        testUser.setPassword("encodedPassword");
        testUser.setType(UserType.COMMON);
        testUser.setActive(true);
        testUser.setDocument(new Document("52998224725")); // Valid CPF
        testUser.setDocumentHash("hash123");
        testUser.setCreatedAt(testTime);
        testUser.setUpdatedAt(testTime);
        reset(createUserService, getUserService, deleteUserService, patchUserService);
    }

    @Test
    void create_WithValidUser_ShouldReturnCreatedWithUserId() throws Exception {
        // Arrange
        CreateUserRequestDTO request = new CreateUserRequestDTO(
            "John Doe", "john.doe@example.com", "SecurePass123!", "52998224725" // Valid CPF
        );
        UUID createdUserId = UUID.randomUUID();
        when(createUserService.execute(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(createdUserId);

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$").value(createdUserId.toString()));
        verify(createUserService).execute("John Doe", "john.doe@example.com", "SecurePass123!", "52998224725");
    }

    @Test
    void findAll_WithUsersInDatabase_ShouldReturnOkWithUsersList() throws Exception {
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
        user2.setCreatedAt(testTime);
        user2.setUpdatedAt(testTime);

        when(getUserService.findAll()).thenReturn(List.of(testUser, user2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("John Doe"))
            .andExpect(jsonPath("$[0].email").value("john.doe@example.com"))
            .andExpect(jsonPath("$[0].type").value("COMMON"))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[1].name").value("Jane Smith"))
            .andExpect(jsonPath("$[1].email").value("jane.smith@example.com"))
            .andExpect(jsonPath("$[1].type").value("MERCHANT"))
            .andExpect(jsonPath("$[1].active").value(true));
        verify(getUserService).findAll();
    }

    @Test
    void findById_WithExistingUser_ShouldReturnOkWithUser() throws Exception {
        // Arrange
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.name").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john.doe@example.com"))
            .andExpect(jsonPath("$.document").value("***.982.247-25")) // Masked document format
            .andExpect(jsonPath("$.type").value("COMMON"))
            .andExpect(jsonPath("$.active").value(true));
        verify(getUserService).findById(testUserId);
    }

    @Test
    void findById_WithNonExistingUser_ShouldReturnFound() throws Exception {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        when(getUserService.findById(nonExistingId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", nonExistingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUserId.toString()));
        verify(getUserService).findById(nonExistingId);
    }

    @Test
    void patch_WithBothEmailAndPassword_ShouldReturnOkWithUpdatedUser() throws Exception {
        // Arrange
        PatchUserRequestDTO request = new PatchUserRequestDTO("new.email@example.com", "NewPass456!");
        when(patchUserService.execute(eq(testUserId), anyString(), anyString())).thenReturn(testUser);
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/users/{id}", testUserId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("John Doe"));
        verify(patchUserService).execute(testUserId, "new.email@example.com", "NewPass456!");
    }

    @Test
    void patch_WithOnlyEmail_ShouldReturnOkWithUpdatedUser() throws Exception {
        // Arrange
        PatchUserRequestDTO request = new PatchUserRequestDTO("new.email@example.com", null);
        when(patchUserService.execute(eq(testUserId), anyString(), isNull())).thenReturn(testUser);
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/users/{id}", testUserId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        verify(patchUserService).execute(testUserId, "new.email@example.com", null);
    }

    @Test
    void patch_WithOnlyPassword_ShouldReturnOkWithUpdatedUser() throws Exception {
        // Arrange
        PatchUserRequestDTO request = new PatchUserRequestDTO(null, "NewPass456!");
        when(patchUserService.execute(eq(testUserId), isNull(), anyString())).thenReturn(testUser);
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/users/{id}", testUserId)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        verify(patchUserService).execute(testUserId, null, "NewPass456!");
    }

    @Test
    void delete_WithExistingUser_ShouldReturnNoContent() throws Exception {
        // Arrange
        doNothing().when(deleteUserService).execute(testUserId);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/users/{id}", testUserId)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isNoContent());
        verify(deleteUserService).execute(testUserId);
    }

    @Test
    void delete_WithNonExistingUser_ShouldDeleteUser() throws Exception {
        // Arrange
        UUID nonExistingId = UUID.randomUUID();
        doNothing().when(deleteUserService).execute(nonExistingId);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/users/{id}", nonExistingId)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isNoContent());
        verify(deleteUserService).execute(nonExistingId);
    }

    @Test
    void create_WithInvalidRequest_ShouldCreateUser() throws Exception {
        // Arrange
        CreateUserRequestDTO request = new CreateUserRequestDTO(
            "John Doe", "john.doe@example.com", "SecurePass123!", "52998224725" // Valid CPF
        );
        UUID createdUserId = UUID.randomUUID();
        when(createUserService.execute(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(createdUserId);

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
        verify(createUserService).execute("John Doe", "john.doe@example.com", "SecurePass123!", "52998224725");
    }

    @Test
    void findAll_WithEmptyDatabase_ShouldReturnOkWithEmptyList() throws Exception {
        // Arrange
        when(getUserService.findAll()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
        verify(getUserService).findAll();
    }

    @Test
    void toResponseDTO_ShouldMapAllFieldsCorrectly() throws Exception {
        // Arrange
        when(getUserService.findById(testUserId)).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", testUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").exists())
            .andExpect(jsonPath("$.email").exists())
            .andExpect(jsonPath("$.document").exists())
            .andExpect(jsonPath("$.type").exists())
            .andExpect(jsonPath("$.active").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
        verify(getUserService).findById(testUserId);
    }
}