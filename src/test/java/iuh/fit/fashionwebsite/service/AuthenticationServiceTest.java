package iuh.fit.fashionwebsite.service;

import iuh.fit.fashionwebsite.dto.request.IntrospectRequest;
import iuh.fit.fashionwebsite.dto.request.LogoutRequest;
import iuh.fit.fashionwebsite.dto.request.RefreshTokenRequest;
import iuh.fit.fashionwebsite.dto.response.AuthenticationResponse;
import iuh.fit.fashionwebsite.dto.response.IntrospectResponse;
import iuh.fit.fashionwebsite.entity.InvalidatedToken;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.enums.TokenType;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.repository.InvalidatedTokenRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvalidatedTokenRepository invalidatedTokenRepository;

    private AuthenticationService authenticationService;

    private final String signerKey = "59a7cd85af4c99bea27a6518cbdb866000967e4170222736e641e209b482c9a0";

    private User user;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(userRepository, invalidatedTokenRepository);
        ReflectionTestUtils.setField(authenticationService, "SINGER_KEY", signerKey);
        ReflectionTestUtils.setField(authenticationService, "VALID_DURATION", 3600L);
        ReflectionTestUtils.setField(authenticationService, "REFRESHABLE_DURATION", 604800L);

        user = User.builder()
                .username("testuser")
                .role(RoleUser.CUSTOMER)
                .tokenVersion(0)
                .build();
    }

    private String generate(TokenType type) throws Exception {
        Method m = AuthenticationService.class.getDeclaredMethod("generateToken", User.class, TokenType.class);
        m.setAccessible(true);
        return (String) m.invoke(authenticationService, user, type);
    }

    private void stubValidLookup() {
        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    }

    @Test
    void introspect_withValidToken_shouldReturnTrue() throws Exception {
        stubValidLookup();
        String token = generate(TokenType.ACCESS);

        IntrospectResponse response = authenticationService.introspect(
                IntrospectRequest.builder().token(token).build()
        );

        assertTrue(response.isValid());
    }

    @Test
    void introspect_withBearerPrefix_shouldReturnTrue() throws Exception {
        stubValidLookup();
        String token = generate(TokenType.ACCESS);

        IntrospectResponse response = authenticationService.introspect(
                IntrospectRequest.builder().token("Bearer " + token).build()
        );

        assertTrue(response.isValid());
    }

    @Test
    void introspect_withInvalidToken_shouldReturnFalseWithoutThrowing() {
        IntrospectResponse response = authenticationService.introspect(
                IntrospectRequest.builder().token("invalid.token.here").build()
        );

        assertFalse(response.isValid());
    }

    @Test
    void introspect_withNullOrEmptyToken_shouldReturnFalseWithoutThrowing() {
        assertFalse(authenticationService.introspect(
                IntrospectRequest.builder().token(null).build()).isValid());
        assertFalse(authenticationService.introspect(
                IntrospectRequest.builder().token("").build()).isValid());
    }

    @Test
    void introspect_withRefreshToken_shouldReturnFalse() throws Exception {
        String refreshToken = generate(TokenType.REFRESH);

        assertFalse(authenticationService.introspect(
                IntrospectRequest.builder().token(refreshToken).build()).isValid());
    }

    @Test
    void introspect_withRevokedToken_shouldReturnFalse() throws Exception {
        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(true);
        String token = generate(TokenType.ACCESS);

        assertFalse(authenticationService.introspect(
                IntrospectRequest.builder().token(token).build()).isValid());
    }

    @Test
    void verifyToken_afterPasswordChange_shouldBeRejected() throws Exception {
        String token = generate(TokenType.ACCESS);
        // Mô phỏng đổi mật khẩu: tokenVersion tăng lên
        User changed = User.builder().username("testuser").role(RoleUser.CUSTOMER).tokenVersion(1).build();
        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(changed));

        AppException ex = assertThrows(AppException.class,
                () -> authenticationService.verifyToken(token, TokenType.ACCESS));
        assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    void refreshToken_shouldRotateAndReturnNewPair() throws Exception {
        stubValidLookup();
        String refreshToken = generate(TokenType.REFRESH);

        AuthenticationResponse response = authenticationService.refreshToken(
                RefreshTokenRequest.builder().refreshToken(refreshToken).build()
        );

        assertNotNull(response.getToken());
        assertNotNull(response.getRefreshToken());
        assertNotEquals(refreshToken, response.getRefreshToken());
        verify(invalidatedTokenRepository).save(any(InvalidatedToken.class));
    }

    @Test
    void refreshToken_withAccessToken_shouldThrow() throws Exception {
        String accessToken = generate(TokenType.ACCESS);

        assertThrows(AppException.class, () -> authenticationService.refreshToken(
                RefreshTokenRequest.builder().refreshToken(accessToken).build()));
        verify(invalidatedTokenRepository, never()).save(any());
    }

    @Test
    void logout_shouldInvalidateAccessAndRefreshTokens() throws Exception {
        stubValidLookup();
        String accessToken = generate(TokenType.ACCESS);
        String refreshToken = generate(TokenType.REFRESH);

        authenticationService.logout(LogoutRequest.builder()
                .token("Bearer " + accessToken)
                .refreshToken(refreshToken)
                .build());

        verify(invalidatedTokenRepository, times(2)).save(any(InvalidatedToken.class));
    }

    @Test
    void logout_withInvalidToken_shouldNotThrow() {
        assertDoesNotThrow(() -> authenticationService.logout(
                LogoutRequest.builder().token("invalid.token.here").build()));
        verify(invalidatedTokenRepository, never()).save(any());
    }

    @Test
    void testJacksonDeserialization() throws Exception {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        String json = "{\"username\":\"nam01\",\"password_hash\":\"123456\"}";
        iuh.fit.fashionwebsite.dto.request.AuthenticationRequest req = 
            mapper.readValue(json, iuh.fit.fashionwebsite.dto.request.AuthenticationRequest.class);
        System.out.println("DESERIALIZED USERNAME: " + req.getUsername());
        System.out.println("DESERIALIZED PASSWORD_HASH: " + req.getPassword_hash());
    }
}
