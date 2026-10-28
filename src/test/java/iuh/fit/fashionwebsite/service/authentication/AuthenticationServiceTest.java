package iuh.fit.fashionwebsite.service.authentication;

import iuh.fit.fashionwebsite.dto.request.ForgotPasswordRequest;
import iuh.fit.fashionwebsite.dto.request.ResetPasswordRequest;
import iuh.fit.fashionwebsite.entity.PasswordResetToken;
import iuh.fit.fashionwebsite.entity.User;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("customer1@example.com")
                .build();

        authenticationService.forgotPassword(request);

        verify(passwordResetTokenRepository).invalidateAllActiveTokensByEmail("customer1@example.com");

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

        assertTrue(token.isUsed());
        verify(passwordResetTokenRepository).save(token);
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
}

