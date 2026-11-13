package iuh.fit.fashionwebsite.service.authentication;

import iuh.fit.fashionwebsite.dto.request.auth.ForgotPasswordRequest;
import iuh.fit.fashionwebsite.dto.request.auth.GoogleLoginRequest;
import iuh.fit.fashionwebsite.dto.request.auth.ResetPasswordRequest;
import iuh.fit.fashionwebsite.dto.response.auth.AuthenticationResponse;
import iuh.fit.fashionwebsite.entity.PasswordResetToken;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.StatusUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.mapper.UserMapper;
import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import iuh.fit.fashionwebsite.repository.PasswordResetTokenRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import iuh.fit.fashionwebsite.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private InvalidatedTokenRepository invalidatedTokenRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private JwtDecoder googleJwtDecoder;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                userMapper,
                invalidatedTokenRepository,
                cloudinaryService,
                passwordEncoder,
                passwordResetTokenRepository,
                emailService
        );
        authenticationService.SIGNER_KEY = "032866f601cbcc25563a1b392701088a8aac77e7a0f62c0520ebf5426da11909bb651c087e95ebae570b776e118b5223e3889100057ac082f0d2baeecebebc9d";
        authenticationService.ISSUER = "fashion-website";
        authenticationService.VALID_DURATION = 900L;
        authenticationService.REFRESHABLE_DURATION = 604800L;
        authenticationService.googleClientId = "my-google-client-id.apps.googleusercontent.com";
        authenticationService.googleJwtDecoder = googleJwtDecoder;
    }

    @Test
    void forgotPassword_Success() {
        User user = User.builder()
                .id(1L)
                .username("customer1")
                .email("customer1@example.com")
                .status(StatusUser.ACTIVE)
                .build();

        when(userRepository.findByEmail("customer1@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByEmailOrderByExpiryDateDesc("customer1@example.com"))
                .thenReturn(Optional.empty());

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("customer1@example.com")
                .build();

        authenticationService.forgotPassword(request);

        verify(passwordResetTokenRepository).deleteAllByEmail("customer1@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals("customer1@example.com", savedToken.getEmail());
        assertNotNull(savedToken.getToken());
        assertFalse(savedToken.isUsed());
        assertTrue(savedToken.getExpiryDate().isAfter(Instant.now()));

        verify(emailService).sendPasswordResetEmail(eq("customer1@example.com"), eq("customer1"), eq(savedToken.getToken()));
    }

    @Test
    void forgotPassword_Cooldown_ThrowsTooManyRequests() {
        User user = User.builder()
                .id(1L)
                .username("customer1")
                .email("customer1@example.com")
                .status(StatusUser.ACTIVE)
                .build();

        PasswordResetToken recentToken = PasswordResetToken.builder()
                .email("customer1@example.com")
                .token("Recent12")
                .expiryDate(Instant.now().plus(14, ChronoUnit.MINUTES).plus(30, ChronoUnit.SECONDS))
                .build();

        when(userRepository.findByEmail("customer1@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByEmailOrderByExpiryDateDesc("customer1@example.com"))
                .thenReturn(Optional.of(recentToken));

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("customer1@example.com")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.forgotPassword(request));
        assertEquals(ErrorCode.RESET_PASSWORD_COOLDOWN, ex.getErrorCode());
        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPassword_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("notfound@example.com")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.forgotPassword(request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(emailService);
    }

    @Test
    void resetPassword_Success() {
        User user = User.builder()
                .id(1L)
                .username("customer1")
                .email("customer1@example.com")
                .passwordHash("oldHashedPassword")
                .status(StatusUser.ACTIVE)
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .id(10L)
                .email("customer1@example.com")
                .token("Abc12345")
                .expiryDate(Instant.now().plus(10, ChronoUnit.MINUTES))
                .used(false)
                .build();

        when(userRepository.findByEmail("customer1@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewSecret123", "oldHashedPassword")).thenReturn(false);
        when(passwordResetTokenRepository.findByEmailAndTokenAndUsedFalse("customer1@example.com", "Abc12345"))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecret123")).thenReturn("newHashedPassword");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("customer1@example.com")
                .tempPassword("Abc12345")
                .newPassword("NewSecret123")
                .confirmPassword("NewSecret123")
                .build();

        authenticationService.resetPassword(request);

        verify(passwordResetTokenRepository).delete(token);
        assertEquals("newHashedPassword", user.getPasswordHash());
        assertNotNull(user.getPasswordChangedAt());
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_PasswordMismatch() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("customer1@example.com")
                .tempPassword("Abc12345")
                .newPassword("NewSecret123")
                .confirmPassword("DifferentSecret123")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.resetPassword(request));
        assertEquals(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH, ex.getErrorCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void resetPassword_NewPasswordSameAsOld() {
        User user = User.builder()
                .id(1L)
                .username("customer1")
                .email("customer1@example.com")
                .passwordHash("hashedCurrentPassword")
                .status(StatusUser.ACTIVE)
                .build();

        when(userRepository.findByEmail("customer1@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPassword123", "hashedCurrentPassword")).thenReturn(true);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("customer1@example.com")
                .tempPassword("Abc12345")
                .newPassword("CurrentPassword123")
                .confirmPassword("CurrentPassword123")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.resetPassword(request));
        assertEquals(ErrorCode.NEW_PASSWORD_SAME_AS_OLD, ex.getErrorCode());
    }

    @Test
    void resetPassword_InvalidToken() {
        User user = User.builder()
                .id(1L)
                .username("customer1")
                .email("customer1@example.com")
                .passwordHash("oldHashed")
                .status(StatusUser.ACTIVE)
                .build();

        when(userRepository.findByEmail("customer1@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewSecret123", "oldHashed")).thenReturn(false);
        when(passwordResetTokenRepository.findByEmailAndTokenAndUsedFalse("customer1@example.com", "WrongToken"))
                .thenReturn(Optional.empty());

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("customer1@example.com")
                .tempPassword("WrongToken")
                .newPassword("NewSecret123")
                .confirmPassword("NewSecret123")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.resetPassword(request));
        assertEquals(ErrorCode.RESET_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    void resetPassword_ExpiredToken() {
        User user = User.builder()
                .id(1L)
                .username("customer1")
                .email("customer1@example.com")
                .passwordHash("oldHashed")
                .status(StatusUser.ACTIVE)
                .build();

        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(10L)
                .email("customer1@example.com")
                .token("Abc12345")
                .expiryDate(Instant.now().minus(5, ChronoUnit.MINUTES))
                .used(false)
                .build();

        when(userRepository.findByEmail("customer1@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewSecret123", "oldHashed")).thenReturn(false);
        when(passwordResetTokenRepository.findByEmailAndTokenAndUsedFalse("customer1@example.com", "Abc12345"))
                .thenReturn(Optional.of(expiredToken));

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("customer1@example.com")
                .tempPassword("Abc12345")
                .newPassword("NewSecret123")
                .confirmPassword("NewSecret123")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.resetPassword(request));
        assertEquals(ErrorCode.RESET_TOKEN_EXPIRED, ex.getErrorCode());
    }

    @Test
    void authenticateWithGoogle_NewUser_AutoRegistersAndReturnsTokens() {
        Jwt googleJwt = new Jwt(
                "valid-google-id-token",
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of("my-google-client-id.apps.googleusercontent.com"),
                        "email", "newuser@gmail.com",
                        "email_verified", true,
                        "name", "Nguyen Van Truong",
                        "picture", "https://lh3.googleusercontent.com/avatar.png"
                )
        );

        when(googleJwtDecoder.decode("valid-google-id-token")).thenReturn(googleJwt);
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedRandomPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-google-id-token")
                .build();

        AuthenticationResponse response = authenticationService.authenticateWithGoogle(request);

        assertTrue(response.isAuthenticated());
        assertNotNull(response.getToken());
        assertNotNull(response.getRefreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertEquals("newuser", createdUser.getUsername());
        assertEquals("newuser@gmail.com", createdUser.getEmail());
        assertEquals("Nguyen Van Truong", createdUser.getFullname());
        assertEquals("https://lh3.googleusercontent.com/avatar.png", createdUser.getAvt());
        assertEquals(RoleUser.CUSTOMER, createdUser.getRole());
        assertEquals(StatusUser.ACTIVE, createdUser.getStatus());
    }

    @Test
    void authenticateWithGoogle_ExistingUser_LinksAccountAndReturnsTokens() {
        Jwt googleJwt = new Jwt(
                "valid-google-id-token",
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of("my-google-client-id.apps.googleusercontent.com"),
                        "email", "existing@gmail.com",
                        "email_verified", true,
                        "name", "Existing User",
                        "picture", "https://lh3.googleusercontent.com/new-avatar.png"
                )
        );

        User existingUser = User.builder()
                .id(5L)
                .username("existing_username")
                .email("existing@gmail.com")
                .fullname("Old Name")
                .avt(null)
                .passwordHash("oldHash")
                .role(RoleUser.CUSTOMER)
                .status(StatusUser.ACTIVE)
                .build();

        when(googleJwtDecoder.decode("valid-google-id-token")).thenReturn(googleJwt);
        when(userRepository.findByEmail("existing@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-google-id-token")
                .build();

        AuthenticationResponse response = authenticationService.authenticateWithGoogle(request);

        assertTrue(response.isAuthenticated());
        assertNotNull(response.getToken());
        // Giữ nguyên username và fullname cũ, chỉ cập nhật avt đang bị null
        assertEquals("existing_username", existingUser.getUsername());
        assertEquals("Old Name", existingUser.getFullname());
        assertEquals("https://lh3.googleusercontent.com/new-avatar.png", existingUser.getAvt());
    }

    @Test
    void authenticateWithGoogle_InactiveUser_ThrowsUserInactive() {
        Jwt googleJwt = new Jwt(
                "valid-google-id-token",
                Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of("my-google-client-id.apps.googleusercontent.com"),
                        "email", "locked@gmail.com",
                        "email_verified", true
                )
        );

        User lockedUser = User.builder()
                .id(9L)
                .username("locked_user")
                .email("locked@gmail.com")
                .status(StatusUser.INACTIVE)
                .build();

        when(googleJwtDecoder.decode("valid-google-id-token")).thenReturn(googleJwt);
        when(userRepository.findByEmail("locked@gmail.com")).thenReturn(Optional.of(lockedUser));

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-google-id-token")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.authenticateWithGoogle(request));
        assertEquals(ErrorCode.USER_INACTIVE, ex.getErrorCode());
    }

    @Test
    void authenticateWithGoogle_InvalidSignatureOrExpired_ThrowsTokenInvalid() {
        when(googleJwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("Invalid signature"));

        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("bad-token")
                .build();

        AppException ex = assertThrows(AppException.class, () -> authenticationService.authenticateWithGoogle(request));
        assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
    }
}
