package iuh.fit.fashionwebsite.controller.user;/*
 * @ (#) AuthenticationController.java     1.0    7/7/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */

import iuh.fit.fashionwebsite.dto.request.*;
import iuh.fit.fashionwebsite.dto.response.UserResponse;
import jakarta.validation.Valid;
import iuh.fit.fashionwebsite.dto.response.ApiResponse;
import iuh.fit.fashionwebsite.dto.response.AuthenticationResponse;
import iuh.fit.fashionwebsite.dto.response.IntrospectResponse;
import iuh.fit.fashionwebsite.enums.AuthResponseCode;
import iuh.fit.fashionwebsite.service.authentication.AuthenticationService;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * @description
 * @author:NguyenTruong
 * @date:  7/7/2026
 * @version:    1.0
 */
@RestController
@RequestMapping("/auth")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@lombok.extern.slf4j.Slf4j
public class AuthenticationController {
    @Autowired
    AuthenticationService authenticationService;

    @PostMapping("/login")
    ApiResponse<AuthenticationResponse> login(@RequestBody @Valid AuthenticationRequest request) {
        AuthenticationResponse isAuthenticated = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .code(AuthResponseCode.LOGIN_SUCCESS.getCode())
                .message("Login successfully")
                .data(isAuthenticated)
                .build();
    }

    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> introspect(
            @RequestBody @Valid IntrospectRequest request) {

        var introspectResponse = authenticationService.introspect(request);

        if (introspectResponse.isValid()) {
            return ApiResponse.<IntrospectResponse>builder()
                    .code(AuthResponseCode.INTROSPECT_SUCCESS.getCode())
                    .message("Token is valid")
                    .data(introspectResponse)
                    .build();
        }

        return ApiResponse.<IntrospectResponse>builder()
                .code(401)
                .message("Token is invalid")
                .data(introspectResponse)
                .build();
    }

    @PostMapping("/refresh")
    ApiResponse<AuthenticationResponse> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return ApiResponse.<AuthenticationResponse>builder()
                .code(AuthResponseCode.TOKEN_REFRESH_SUCCESS.getCode())
                .message("Token refreshed successfully")
                .data(authenticationService.refreshToken(request))
                .build();
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authenticationService.logout(request);
        return ApiResponse.<Void>builder()
                .code(AuthResponseCode.LOGOUT_SUCCESS.getCode())
                .message("Logout successfully")
                .build();
    }
    @PostMapping("/register")
    ApiResponse<UserResponse> register(
            @RequestBody @Valid UserCreationRequest request) {
        // mặc định đang để ảnh là null
        UserResponse response = authenticationService.register(request, null);

        return ApiResponse.<UserResponse>builder()
                .code(AuthResponseCode.REGISTER_SUCCESS.getCode())
                .message("Register successfully")
                .data(response)
                .build();
    }

}
