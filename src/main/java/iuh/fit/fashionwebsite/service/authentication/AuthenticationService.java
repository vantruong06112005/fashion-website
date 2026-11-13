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
import iuh.fit.fashionwebsite.dto.request.auth.*;
import iuh.fit.fashionwebsite.dto.request.user.UserCreationRequest;
import iuh.fit.fashionwebsite.dto.response.auth.AuthenticationResponse;
import iuh.fit.fashionwebsite.dto.response.auth.IntrospectResponse;
import iuh.fit.fashionwebsite.dto.response.user.UserResponse;
import iuh.fit.fashionwebsite.entity.PasswordResetToken;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.enums.TokenType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.user.UnauthenticatedException;
import iuh.fit.fashionwebsite.mapper.UserMapper;
import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import iuh.fit.fashionwebsite.repository.PasswordResetTokenRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import iuh.fit.fashionwebsite.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String CLAIM_USER_ID = "userId";
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> VALID_GOOGLE_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final CloudinaryService cloudinaryService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    private static final String LOWER = "abcdefghjkmnpqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL_CHARS = LOWER + UPPER + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    @NonFinal
    // Danh sách Google Client ID hợp lệ (cách nhau bởi dấu phẩy nếu có nhiều môi trường Web / Playground)
    @Value("${google.client-id:}")
    protected String googleClientId;

    @NonFinal
    protected volatile JwtDecoder googleJwtDecoder;

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
        if (user.getId() != null) {
            claimsBuilder.claim(CLAIM_USER_ID, user.getId());
        }
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

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ensureActive(user);

        Instant now = Instant.now();
        // Chống spam (Rate-limiting / Cooldown 60 giây):
        // Mỗi mã có hạn 15 phút → nếu hạn còn > 14 phút nghĩa là mã vừa được tạo chưa đầy 60 giây
        passwordResetTokenRepository.findTopByEmailOrderByExpiryDateDesc(email)
                .ifPresent(existingToken -> {
                    if (existingToken.getExpiryDate().minus(14, ChronoUnit.MINUTES).isAfter(now)) {
                        throw new AppException(ErrorCode.RESET_PASSWORD_COOLDOWN);
                    }
                });

        // Xóa cứng toàn bộ mã đặt lại mật khẩu cũ của email này để tránh phình bảng DB
        passwordResetTokenRepository.deleteAllByEmail(email);

        String tempPassword = generateRandomPassword();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(email)
                .token(tempPassword)
                .expiryDate(now.plus(15, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(email, user.getUsername(), tempPassword);
    }

    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ensureActive(user);

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByEmailAndTokenAndUsedFalse(email, request.getTempPassword().trim())
                .orElseThrow(() -> new AppException(ErrorCode.RESET_TOKEN_INVALID));

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            // Xóa luôn mã đã hết hạn khỏi DB
            passwordResetTokenRepository.delete(resetToken);
            throw new AppException(ErrorCode.RESET_TOKEN_EXPIRED);
        }

        // Xóa cứng mã tạm ngay sau khi sử dụng thành công để bảng luôn gọn nhẹ
        passwordResetTokenRepository.delete(resetToken);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        log.info("Password successfully reset for user {}", user.getUsername());
    }

    /**
     * Đăng nhập hoặc tự động đăng ký / liên kết tài khoản qua Google ID Token (OAuth2 / OIDC)
     */
    public AuthenticationResponse authenticateWithGoogle(GoogleLoginRequest request) {
        Jwt googleJwt = verifyGoogleIdToken(request.getIdToken());

        String email = googleJwt.getClaimAsString("email");
        Boolean emailVerified = googleJwt.getClaimAsBoolean("email_verified");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String normalizedEmail = email.trim().toLowerCase();
        String fullName = googleJwt.getClaimAsString("name");
        String picture = googleJwt.getClaimAsString("picture");

        User user = userRepository.findByEmail(normalizedEmail)
                .map(existingUser -> {
                    // Nếu tài khoản đã tồn tại nhưng bị Admin khóa thì từ chối đăng nhập
                    ensureActive(existingUser);
                    boolean updated = false;
                    // Bổ sung ảnh đại diện từ Google nếu tài khoản cũ chưa có avatar
                    if ((existingUser.getAvt() == null || existingUser.getAvt().isBlank())
                            && picture != null && !picture.isBlank()) {
                        existingUser.setAvt(picture);
                        updated = true;
                    }
                    // Bổ sung họ tên từ Google nếu tài khoản cũ chưa có fullname
                    if ((existingUser.getFullname() == null || existingUser.getFullname().isBlank())
                            && fullName != null && !fullName.isBlank()) {
                        existingUser.setFullname(fullName);
                        updated = true;
                    }
                    return updated ? userRepository.save(existingUser) : existingUser;
                })
                .orElseGet(() -> createGoogleUser(normalizedEmail, fullName, picture));

        return buildAuthenticationResponse(user);
    }

    private Jwt verifyGoogleIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        if (idToken.startsWith("Bearer ")) {
            idToken = idToken.substring(7).trim();
        }
        try {
            Jwt jwt = getGoogleJwtDecoder().decode(idToken);

            String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : jwt.getClaimAsString("iss");
            if (issuer == null || !VALID_GOOGLE_ISSUERS.contains(issuer)) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }

            if (googleClientId != null && !googleClientId.isBlank()) {
                Set<String> allowedClientIds = Arrays.stream(googleClientId.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                List<String> audience = jwt.getAudience();
                if (!allowedClientIds.isEmpty()
                        && (audience == null || audience.stream().noneMatch(allowedClientIds::contains))) {
                    throw new AppException(ErrorCode.TOKEN_INVALID);
                }
            }

            return jwt;
        } catch (JwtException e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    private JwtDecoder getGoogleJwtDecoder() {
        if (googleJwtDecoder == null) {
            synchronized (this) {
                if (googleJwtDecoder == null) {
                    googleJwtDecoder = NimbusJwtDecoder
                            .withJwkSetUri(GOOGLE_JWK_SET_URI)
                            .build();
                }
            }
        }
        return googleJwtDecoder;
    }

    private User createGoogleUser(String email, String fullName, String picture) {
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (baseUsername.isBlank()) {
            baseUsername = "user";
        }
        String username = baseUsername;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + "_" + (100 + RANDOM.nextInt(900));
        }

        User newUser = User.builder()
                .username(username)
                .email(email)
                .fullname(fullName != null && !fullName.isBlank() ? fullName : username)
                .avt(picture)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build();
        return userRepository.save(newUser);
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(8);
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        for (int i = 3; i < 8; i++) {
            sb.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }
        char[] array = sb.toString().toCharArray();
        for (int i = array.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
        return new String(array);
    }
}
