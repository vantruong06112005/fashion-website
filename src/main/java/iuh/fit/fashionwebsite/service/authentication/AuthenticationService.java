/*
 * @ (#) AuthenticationService.java     1.0    9/10/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.service.authentication;


import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import iuh.fit.fashionwebsite.dto.request.*;
import iuh.fit.fashionwebsite.dto.response.AuthenticationResponse;
import iuh.fit.fashionwebsite.dto.response.IntrospectResponse;
import iuh.fit.fashionwebsite.dto.response.UserResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.enums.TokenType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.user.UnauthenticatedException;
import iuh.fit.fashionwebsite.mapper.UserMapper;
import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/*
 * @description
 * @author:NguyenTruong
 * @date:  9/10/2026
 * @version:    1.0
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j // Tạo đối tượng log
public class AuthenticationService {
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_SCOPE = "scope";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final CloudinaryService cloudinaryService;
    private final PasswordEncoder passwordEncoder;

    @NonFinal
    // Đọc khóa bí mật từ biến môi trường JWT_SIGNER_KEY (qua application.properties)
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    // Đơn vị phát hành token (claim "iss"), được CustomJwtDecoder kiểm tra
    @Value("${jwt.issuer}")
    protected String ISSUER;

    @NonFinal
    // Thời gian sống của access token (giây)
    @Value("${jwt.valid-duration:3600}")
    protected long VALID_DURATION;

    @NonFinal
    // Thời gian sống của refresh token (giây)
    @Value("${jwt.refreshable-duration:604800}")
    protected long REFRESHABLE_DURATION;

    /**
     * Xác thực đăng nhập
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Sai username hay sai mật khẩu đều trả về cùng một lỗi
        // → không tiết lộ username nào đang tồn tại (chống user enumeration)
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(UnauthenticatedException::new);
        // So sánh mật khẩu người dùng nhập với mật khẩu đã băm trong database
        if (request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthenticatedException();
        }
        // Tài khoản bị khóa / chưa kích hoạt thì không cho đăng nhập
        ensureActive(user);
        // Tạo cặp access/refresh token nếu đăng nhập thành công
        return buildAuthenticationResponse(user);
    }

    // Kiểm tra tính hợp lệ của JWT Token (access token)
    @Transactional(readOnly = true)
    public IntrospectResponse introspect(IntrospectRequest request) {
        boolean isValid = true;
        try {
            verifyToken(request.getToken(), TokenType.ACCESS);
        } catch (Exception e) {
            log.warn("Token introspection failed: {}", e.getMessage());
            isValid = false;
        }
        return IntrospectResponse.builder().valid(isValid).build();
    }

    /**
     * Cấp lại cặp token mới từ refresh token.
     * Refresh token cũ bị thu hồi ngay (rotation) để không thể dùng lại.
     */
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        SignedJWT signedJWT = verifyToken(request.getRefreshToken(), TokenType.REFRESH);

        try {
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            User user = userRepository.findByUsername(claims.getSubject())
                    .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

            // Refresh token phát hành trước lần đổi mật khẩu gần nhất → không còn hiệu lực
            if (isIssuedBeforePasswordChange(claims, user)) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }
            ensureActive(user);

            // Thu hồi nguyên tử: nếu token đã bị dùng (request song song / replay) thì từ chối
            if (!invalidate(signedJWT)) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }
            return buildAuthenticationResponse(user);
        } catch (ParseException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * Đăng xuất: thu hồi access token và refresh token (nếu có).
     * Token không hợp lệ / đã hết hạn sẽ được bỏ qua.
     */
    public void logout(LogoutRequest request) {
        invalidateQuietly(request.getToken(), TokenType.ACCESS);
        invalidateQuietly(request.getRefreshToken(), TokenType.REFRESH);
    }

    /**
     * Xác minh token: chữ ký, hạn dùng, loại token, chưa bị thu hồi
     * và user tồn tại trong hệ thống.
     */
    @Transactional(readOnly = true)
    public SignedJWT verifyToken(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        // Tự động loại bỏ tiền tố "Bearer " nếu người dùng gửi kèm
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes(StandardCharsets.UTF_8));
            if (!signedJWT.verify(verifier)) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || !expirationTime.after(new Date())) {
                throw new AppException(ErrorCode.TOKEN_EXPIRED);
            }

            if (!ISSUER.equals(claims.getIssuer())) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }

            if (!expectedType.name().equals(claims.getStringClaim(CLAIM_TYPE))) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }

            String jti = claims.getJWTID();
            if (jti == null || invalidatedTokenRepository.existsById(jti)) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }

            if (!userRepository.existsByUsername(claims.getSubject())) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }

            return signedJWT;
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    // Chặn tài khoản bị khóa. status = null (dữ liệu cũ) vẫn được coi là hoạt động.
    private void ensureActive(User user) {
        if (user.getStatus() == StatusUser.INACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
    }

    // So sánh theo giây vì claim iat của JWT chỉ có độ chính xác tới giây
    private boolean isIssuedBeforePasswordChange(JWTClaimsSet claims, User user) {
        if (user.getPasswordChangedAt() == null) {
            return false;
        }
        Date issuedAt = claims.getIssueTime();
        if (issuedAt == null) {
            return true;
        }
        return issuedAt.toInstant().getEpochSecond() < user.getPasswordChangedAt().getEpochSecond();
    }

    private AuthenticationResponse buildAuthenticationResponse(User user) {
        return AuthenticationResponse.builder()
                .token(generateToken(user, TokenType.ACCESS))
                .refreshToken(generateToken(user, TokenType.REFRESH))
                .authenticated(true)
                .build();
    }

    private void invalidateQuietly(String token, TokenType type) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            invalidate(verifyToken(token, type));
        } catch (AppException e) {
            log.debug("Skip invalidating {} token: {}", type, e.getErrorCode());
        }
    }

    /**
     * Đưa jti vào blacklist. Trả về false nếu token đã bị thu hồi từ trước.
     */
    private boolean invalidate(SignedJWT signedJWT) {
        try {
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            return invalidatedTokenRepository.insertIfAbsent(
                    claims.getJWTID(),
                    claims.getExpirationTime().toInstant()) > 0;
        } catch (ParseException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    private String generateToken(User user, TokenType type) {
        long duration = type == TokenType.ACCESS ? VALID_DURATION : REFRESHABLE_DURATION;
        // Khai báo thuật toán ký HS512
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
       
        // Khai báo các thông tin (claims) của JWT
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                // Người sở hữu token
                .subject(user.getUsername())
                // Đơn vị phát hành token
                .issuer(ISSUER)
                // Thời điểm tạo token
                .issueTime(new Date())
                // Thời điểm hết hạn
                .expirationTime(new Date(Instant.now().plus(duration, ChronoUnit.SECONDS).toEpochMilli()))
                // ID duy nhất của token, dùng để thu hồi
                .jwtID(UUID.randomUUID().toString())
                // Loại token: ACCESS / REFRESH
                .claim(CLAIM_TYPE, type.name());
        // Chỉ access token mới mang quyền
        if (type == TokenType.ACCESS) {
            claimsBuilder.claim(CLAIM_SCOPE, buildScope(user));
        }
        // Chuyển Claims thành Payload
        Payload payload = new Payload(claimsBuilder.build().toJSONObject());
        // Tạo JWT Object gồm Header và Payload
        JWSObject jwsObject = new JWSObject(header, payload);
        try {

            // Ký JWT bằng khóa bí mật
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes(StandardCharsets.UTF_8)));

            // Chuyển JWT thành chuỗi để trả về client
            return jwsObject.serialize();

        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildScope(User user) {
        if (user.getRole() == null)     {
            return "";
        }
        return user.getRole().name();
    }
    public UserResponse register(UserCreationRequest request, MultipartFile file) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = userMapper.toUser(request);

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setRole(RoleUser.CUSTOMER);
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

}
