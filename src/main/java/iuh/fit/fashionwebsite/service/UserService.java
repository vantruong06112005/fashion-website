/*
 * @ (#) UserService.java     1.0    8/29/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.service;

import iuh.fit.fashionwebsite.dto.request.ChangePasswordRequest;
import iuh.fit.fashionwebsite.dto.request.UserCreationRequest;
import iuh.fit.fashionwebsite.dto.request.UserUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.UserResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.user.UserNotFoundException;
import iuh.fit.fashionwebsite.mapper.UserMapper;
import iuh.fit.fashionwebsite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
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

        user.setRole(RoleUser.CUSTOMER);

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

        if (file != null && !file.isEmpty()) {
            String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
            user.setAvt(avatarUrl);
        } else if (request.getAvt() != null && !request.getAvt().isBlank()) {
            user.setAvt(request.getAvt());
        }

        if (request.getPassword() != null
                && !request.getPassword().isBlank()) {
            user.setPasswordHash(
                    passwordEncoder.encode(request.getPassword())
            );
            // Đổi mật khẩu => vô hiệu hoá toàn bộ token cũ
            user.setTokenVersion(
                    (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1
            );
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

        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException();
        }

        userRepository.deleteById(id);
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

        String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
        user.setAvt(avatarUrl);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    public UserResponse uploadCurrentAvatar(MultipartFile file) {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(UserNotFoundException::new);

        String avatarUrl = cloudinaryService.uploadImage(file, "fashion_website/avatars");
        user.setAvt(avatarUrl);

        return userMapper.toUserResponse(userRepository.save(user));
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

        userRepository.updatePassword(
                user.getId(),
                passwordEncoder.encode(request.getNewPassword())
        );

        log.info("User '{}' changed password successfully", username);
    }
}