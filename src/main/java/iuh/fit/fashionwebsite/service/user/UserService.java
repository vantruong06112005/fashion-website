/*
 * @ (#) UserService.java     1.0    8/29/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.service.user;

import iuh.fit.fashionwebsite.dto.request.ChangePasswordRequest;
import iuh.fit.fashionwebsite.dto.request.user.UserCreationRequest;
import iuh.fit.fashionwebsite.dto.request.user.UserUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.user.UserResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.user.UserNotFoundException;
import iuh.fit.fashionwebsite.mapper.UserMapper;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;

    public UserResponse createUser(UserCreationRequest request) {
        return createUser(request, null);
    }
    // get user hien tai
    public User getCurrentUserEntity() {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);
    }

    public UserResponse createUser(UserCreationRequest request, MultipartFile file) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.DUPLICATE_USERNAME);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = userMapper.toUser(request);

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setRole(request.getRole() != null ? request.getRole() : RoleUser.CUSTOMER);
        user.setStatus(StatusUser.ACTIVE);

        if (file != null && !file.isEmpty()) {
            String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
            user.setAvt(avatarUrl);
        } else if (request.getAvt() != null && !request.getAvt().isBlank()) {
            user.setAvt(request.getAvt());
        }

        return userMapper.toUserResponse(
                userRepository.save(user)
        );
    }

    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        return updateUser(id, request, null);
    }

    public UserResponse updateUser(Long id, UserUpdateRequest request, MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        if (request.getEmail() != null
                && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }

        userMapper.updateUser(user, request);

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        String oldAvatar = user.getAvt();

        if (file != null && !file.isEmpty()) {
            String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
            registerRollbackCleanup(avatarUrl);
            user.setAvt(avatarUrl);
            if (oldAvatar != null && !oldAvatar.isBlank() && !oldAvatar.equals(avatarUrl)) {
                deleteOldAvatarSafely(oldAvatar);
            }
        } else if (request.getAvt() != null) {
            if (request.getAvt().isBlank()) {
                // Người dùng chủ động xóa ảnh đại diện (gửi chuỗi rỗng)
                user.setAvt(null);
                if (oldAvatar != null && !oldAvatar.isBlank()) {
                    deleteOldAvatarSafely(oldAvatar);
                }
            } else if (!request.getAvt().equals(oldAvatar)) {
                user.setAvt(request.getAvt());
                if (oldAvatar != null && !oldAvatar.isBlank()) {
                    deleteOldAvatarSafely(oldAvatar);
                }
            }
        }

        // Chỉ ADMIN đi qua đường này (PUT /users/{id}) → cho phép reset mật khẩu.
        // Đánh dấu thời điểm đổi để vô hiệu hoá các refresh token cũ của user.
        if (request.getPassword() != null
                && !request.getPassword().isBlank()) {
            user.setPasswordHash(
                    passwordEncoder.encode(request.getPassword())
            );
            user.setPasswordChangedAt(Instant.now());
        }

        return userMapper.toUserResponse(
                userRepository.save(user)
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toUserResponse(user);
    }

//    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {

        log.info("Fetching all users from the database");

        return userRepository.findAll()
                .stream()
                .map(userMapper::toUserResponse)
                .toList();
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        // Xóa mềm: Chuyển trạng thái sang INACTIVE và vô hiệu hoá toàn bộ token cũ
        user.setStatus(StatusUser.INACTIVE);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        log.info("Soft-deleted (deactivated) user with id: {}", id);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {

        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toUserResponse(user);
    }

    public UserResponse updateCurrentUser(UserUpdateRequest request, MultipartFile file) {
        // Người dùng tự đổi mật khẩu phải qua PUT /users/change-password (có xác minh mật khẩu cũ)
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        // Không cho phép tự đổi role hoặc trạng thái hoạt động của chính mình
        request.setRole(null);
        request.setStatus(null);

        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);

        return updateUser(user.getId(), request, file);
    }

    public UserResponse uploadAvatar(Long id, MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        String oldAvatar = user.getAvt();
        String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
        registerRollbackCleanup(avatarUrl);
        user.setAvt(avatarUrl);

        User savedUser = userRepository.save(user);

        if (oldAvatar != null && !oldAvatar.isBlank() && !oldAvatar.equals(avatarUrl)) {
            deleteOldAvatarSafely(oldAvatar);
        }

        return userMapper.toUserResponse(savedUser);
    }

    public UserResponse uploadCurrentAvatar(MultipartFile file) {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);

        String oldAvatar = user.getAvt();
        String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
        registerRollbackCleanup(avatarUrl);
        user.setAvt(avatarUrl);

        User savedUser = userRepository.save(user);

        if (oldAvatar != null && !oldAvatar.isBlank() && !oldAvatar.equals(avatarUrl)) {
            deleteOldAvatarSafely(oldAvatar);
        }

        return userMapper.toUserResponse(savedUser);
    }

    public UserResponse deleteAvatar(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        String oldAvatar = user.getAvt();
        user.setAvt(null);
        User savedUser = userRepository.save(user);

        if (oldAvatar != null && !oldAvatar.isBlank()) {
            deleteOldAvatarSafely(oldAvatar);
        }

        return userMapper.toUserResponse(savedUser);
    }

    public UserResponse deleteCurrentAvatar() {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);

        return deleteAvatar(user.getId());
    }

    /**
     * Dọn ảnh cũ trên Cloudinary sau khi database transaction đã commit thành công.
     */
    private void deleteOldAvatarSafely(String oldAvatar) {
        if (oldAvatar == null || oldAvatar.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        cloudinaryService.deleteImage(oldAvatar);
                    } catch (Exception e) {
                        log.warn("Failed to delete old avatar from Cloudinary after commit: {}", oldAvatar, e);
                    }
                }
            });
        } else {
            try {
                cloudinaryService.deleteImage(oldAvatar);
            } catch (Exception e) {
                log.warn("Failed to delete old avatar from Cloudinary: {}", oldAvatar, e);
            }
        }
    }

    /**
     * Tự động xóa ảnh vừa tải lên nếu database transaction bị rollback.
     */
    private void registerRollbackCleanup(String newAvatarUrl) {
        if (newAvatarUrl == null || newAvatarUrl.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        log.warn("Database transaction rolled back. Cleaning up uploaded avatar: {}", newAvatarUrl);
                        try {
                            cloudinaryService.deleteImage(newAvatarUrl);
                        } catch (Exception e) {
                            log.error("Failed to clean up uploaded avatar on rollback", e);
                        }
                    }
                }
            });
        }
    }

    /**
     * Đổi mật khẩu cho người dùng đang đăng nhập.
     */
    public void changePassword(ChangePasswordRequest request) {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        // Cập nhật mật khẩu + passwordChangedAt → mọi refresh token cũ không thể dùng để /auth/refresh nữa
        userRepository.updatePassword(
                user.getId(),
                passwordEncoder.encode(request.getNewPassword()),
                Instant.now()
        );

        log.info("User '{}' changed password successfully", username);
    }
}