package iuh.fit.fashionwebsite.service;

import iuh.fit.fashionwebsite.dto.request.IntrospectRequest;
import iuh.fit.fashionwebsite.dto.response.IntrospectResponse;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthenticationService authenticationService;

    private final String signerKey = "59a7cd85af4c99bea27a6518cbdb866000967e4170222736e641e209b482c9a0";

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(userRepository);
        ReflectionTestUtils.setField(authenticationService, "SINGER_KEY", signerKey);
    }

    @Test
    void introspect_withValidToken_shouldReturnTrue() throws Exception {
        User user = User.builder()
                .username("testuser")
                .role(RoleUser.CUSTOMER)
                .build();

        Method generateTokenMethod = AuthenticationService.class.getDeclaredMethod("generateToken", User.class);
        generateTokenMethod.setAccessible(true);
        String token = (String) generateTokenMethod.invoke(authenticationService, user);

        IntrospectResponse response = authenticationService.introspect(
                IntrospectRequest.builder().token(token).build()
        );

        assertNotNull(response);
        assertTrue(response.isValid());
    }

    @Test
    void introspect_withBearerPrefix_shouldReturnTrue() throws Exception {
        User user = User.builder()
                .username("testuser")
                .role(RoleUser.CUSTOMER)
                .build();

        Method generateTokenMethod = AuthenticationService.class.getDeclaredMethod("generateToken", User.class);
        generateTokenMethod.setAccessible(true);
        String token = (String) generateTokenMethod.invoke(authenticationService, user);

        IntrospectResponse response = authenticationService.introspect(
                IntrospectRequest.builder().token("Bearer " + token).build()
        );

        assertNotNull(response);
        assertTrue(response.isValid());
    }

    @Test
    void introspect_withInvalidToken_shouldReturnFalseWithoutThrowing() {
        IntrospectResponse response = authenticationService.introspect(
                IntrospectRequest.builder().token("invalid.token.here").build()
        );

        assertNotNull(response);
        assertFalse(response.isValid());
    }

    @Test
    void introspect_withNullOrEmptyToken_shouldReturnFalseWithoutThrowing() {
        IntrospectResponse nullResponse = authenticationService.introspect(
                IntrospectRequest.builder().token(null).build()
        );
        assertNotNull(nullResponse);
        assertFalse(nullResponse.isValid());

        IntrospectResponse emptyResponse = authenticationService.introspect(
                IntrospectRequest.builder().token("").build()
        );
        assertNotNull(emptyResponse);
        assertFalse(emptyResponse.isValid());
    }
}
