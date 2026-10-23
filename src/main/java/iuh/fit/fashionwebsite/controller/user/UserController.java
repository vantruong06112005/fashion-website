/*
 * @ (#) UserController.java     1.0    8/29/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.controller.user;


/*
 * @description
 * @author:NguyenTruong
 * @date:  8/29/2026
 * @version:    1.0
 */


import iuh.fit.fashionwebsite.dto.request.ChangePasswordRequest;
import iuh.fit.fashionwebsite.dto.request.UserCreationRequest;
import iuh.fit.fashionwebsite.dto.request.UserUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.UserResponse;
import iuh.fit.fashionwebsite.service.user.UserService;
import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import iuh.fit.fashionwebsite.validator.anotation.ValidImageFile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/*
 * @description
 * @author:NguyenTruong
 * @date:  7/7/2026
 * @version:    1.0
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class UserController {
    private final UserService userService;

    // ADMIN tạo tài khoản cho nhân viên
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .message("User has been created successfully")
                .data(userService.createUser(request, null))
                .build();
    }
    // get user hiện tại đang đăng nhập
    @GetMapping("/info/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserResponse> getCurrentUserInfo() {
        return ApiResponse.<UserResponse>builder()
                .message("Fetched current user details successfully")
                .data(userService.getCurrentUser())
                .build();
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> createUserMultipart(
            @ModelAttribute @Valid UserCreationRequest request,
            @RequestParam(value = "file", required = false) @ValidImageFile MultipartFile file
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("User has been created successfully")
                .data(userService.createUser(request, file))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.<List<UserResponse>>builder()
                .message("Fetched all users successfully")
                .data(userService.getAllUsers())
                .build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        return ApiResponse.<UserResponse>builder()
                .message("Fetched user details successfully")
                .data(userService.getUserById(id))
                .build();
    }

    // Các endpoint thao tác theo {id} bất kỳ chỉ dành cho ADMIN (chống IDOR).
    // Người dùng thường cập nhật hồ sơ của chính mình qua /users/info.
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UserUpdateRequest request
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("User has been updated successfully")
                .data(userService.updateUser(id, request, null))
                .build();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateUserMultipart(
            @PathVariable Long id,
            @ModelAttribute @Valid UserUpdateRequest request,
            @RequestParam(value = "file", required = false) @ValidImageFile MultipartFile file
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("User has been updated successfully")
                .data(userService.updateUser(id, request, file))
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.<Void>builder()
                .message("User has been deleted successfully")
                .build();
    }

    @GetMapping("/info")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserResponse> getCurrentUser() {
        return ApiResponse.<UserResponse>builder()
                .message("Fetched current user details successfully")
                .data(userService.getCurrentUser())
                .build();
    }

    @PutMapping(value = "/info", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserResponse> updateCurrentUserInfo(
            @RequestBody @Valid UserUpdateRequest request
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("Current user profile updated successfully")
                .data(userService.updateCurrentUser(request, null))
                .build();
    }

    @PutMapping(value = "/info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserResponse> updateCurrentUserMultipart(
            @ModelAttribute @Valid UserUpdateRequest request,
            @RequestParam(value = "file", required = false) @ValidImageFile MultipartFile file
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("Current user profile updated successfully")
                .data(userService.updateCurrentUser(request, file))
                .build();
    }

    @PutMapping(value = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<Void> changePassword(
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        userService.changePassword(request);
        return ApiResponse.<Void>builder()
                .message("Password has been changed successfully")
                .build();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CUSTOMER')")
    public ApiResponse<UserResponse> uploadCurrentAvatar(
            @RequestParam("file") @ValidImageFile(nullable = false) MultipartFile file
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("Avatar uploaded successfully")
                .data(userService.uploadCurrentAvatar(file))
                .build();
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> uploadAvatar(
            @PathVariable Long id,
            @RequestParam("file") @ValidImageFile(nullable = false) MultipartFile file
    ) {
        return ApiResponse.<UserResponse>builder()
                .message("Avatar uploaded successfully")
                .data(userService.uploadAvatar(id, file))
                .build();
    }
}
