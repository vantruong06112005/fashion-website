package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.request.user.UserUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.user.UserResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.mapper.UserMapper;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceAvatarTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private final String oldAvatarUrl = "https://res.cloudinary.com/n8agmdkc/image/upload/v1792905150/fashion_website/avatars/old_avatar.jpg";
    private final String newAvatarUrl = "https://res.cloudinary.com/n8agmdkc/image/upload/v1792905160/fashion_website/avatars/new_avatar.jpg";

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .fullname("Test User")
                .email("test@gmail.com")
                .avt(oldAvatarUrl)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void mockSecurityContext(String username) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(username);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void uploadCurrentAvatar_DeletesOldAvatarAfterCommit() {
        mockSecurityContext("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(cloudinaryService.uploadImage(eq(file), eq("fashion_website/avatars"))).thenReturn(newAvatarUrl);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(UserResponse.builder().id(1L).avt(newAvatarUrl).build());

        TransactionSynchronizationManager.initSynchronization();
        try {
            UserResponse response = userService.uploadCurrentAvatar(file);

            assertNotNull(response);
            assertEquals(newAvatarUrl, response.getAvt());
            assertEquals(newAvatarUrl, sampleUser.getAvt());

            // Trước khi commit transaction, chưa được xóa ảnh cũ
            verify(cloudinaryService, never()).deleteImage(oldAvatarUrl);

            // Giả lập Spring commit transaction
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            // Sau khi commit, ảnh cũ phải được xóa
            verify(cloudinaryService).deleteImage(oldAvatarUrl);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadCurrentAvatar_RollbackDeletesNewlyUploadedImage() {
        mockSecurityContext("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(cloudinaryService.uploadImage(eq(file), eq("fashion_website/avatars"))).thenReturn(newAvatarUrl);
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB error"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(RuntimeException.class, () -> userService.uploadCurrentAvatar(file));

            // Giả lập Spring rollback transaction
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            // Phải xóa ảnh mới vừa upload để không sinh ảnh rác
            verify(cloudinaryService).deleteImage(newAvatarUrl);
            // Không xóa ảnh cũ vì DB rollback
            verify(cloudinaryService, never()).deleteImage(oldAvatarUrl);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadCurrentAvatar_WhenNoOldAvatar_DoesNotCallDeleteOld() {
        sampleUser.setAvt(null);
        mockSecurityContext("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(cloudinaryService.uploadImage(eq(file), eq("fashion_website/avatars"))).thenReturn(newAvatarUrl);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(UserResponse.builder().id(1L).avt(newAvatarUrl).build());

        userService.uploadCurrentAvatar(file);

        verify(cloudinaryService, never()).deleteImage(anyString());
    }

    @Test
    void deleteCurrentAvatar_DeletesOldAvatarAfterCommit() {
        mockSecurityContext("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(UserResponse.builder().id(1L).avt(null).build());

        TransactionSynchronizationManager.initSynchronization();
        try {
            UserResponse response = userService.deleteCurrentAvatar();

            assertNotNull(response);
            assertNull(response.getAvt());
            assertNull(sampleUser.getAvt());

            for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(cloudinaryService).deleteImage(oldAvatarUrl);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateUser_WithNewMultipartFile_DeletesOldAvatar() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(UserResponse.builder().id(1L).avt(newAvatarUrl).build());

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(cloudinaryService.uploadImage(eq(file), eq("fashion_website/avatars"))).thenReturn(newAvatarUrl);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullname("Updated Name");

        userService.updateUser(1L, request, file);

        verify(cloudinaryService).deleteImage(oldAvatarUrl);
    }

    @Test
    void updateUser_WithBlankAvtRequest_ClearsAvatarAndDeletesOld() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(UserResponse.builder().id(1L).avt(null).build());

        UserUpdateRequest request = new UserUpdateRequest();
        request.setAvt(""); // Blank means user requested clearing avatar

        userService.updateUser(1L, request, null);

        assertNull(sampleUser.getAvt());
        verify(cloudinaryService).deleteImage(oldAvatarUrl);
    }
}
