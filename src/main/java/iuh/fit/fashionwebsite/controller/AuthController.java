///*
// * @ (#) AuthController.java     1.0    8/25/2026
// *
// * Copyright (c) 2026 IUH. All rights reserved.
// */
//package iuh.fit.fashionwebsite.controller;
//
//
//
//import com.nimbusds.jose.JOSEException;
//import lombok.experimental.FieldDefaults;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//
///*
// * @description
// * @author:NguyenTruong
// * @date:  7/7/2026
// * @version:    1.0
// */
//@RestController
//@RequestMapping("/auth")
//@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
//public class AuthenticationController {
//    @Autowired
//    AuthenticationService authenticationService;
//    @PostMapping("/login")
//    ApiResponse<AuthenticationResponse> login(@RequestBody AuthenticationRequest request) {
//        // Implementation for login logic
//        var isAuthenticated = authenticationService.authenticate(request);
//
//        return ApiResponse.<AuthenticationResponse>builder()
//                // có thể có code
//                .data(isAuthenticated)
//                .build();
//    }
//    @PostMapping("/introspect")
//    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request) throws JOSEException {
//        // Implementation for introspect logic
//        var introspectResponse = authenticationService.introspect(request);
//
//        return ApiResponse.<IntrospectResponse>builder()
//                .data(introspectResponse)
//                .build();
//    }
//
//}
