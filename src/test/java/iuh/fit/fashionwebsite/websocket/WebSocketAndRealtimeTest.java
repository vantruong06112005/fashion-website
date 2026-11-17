package iuh.fit.fashionwebsite.websocket;

import iuh.fit.fashionwebsite.controller.chat.ChatWsController;
import iuh.fit.fashionwebsite.dto.request.ChatMessageReplyRequest;
import iuh.fit.fashionwebsite.dto.request.ChatMessageSendRequest;
import iuh.fit.fashionwebsite.dto.response.ChatMessageResponse;
import iuh.fit.fashionwebsite.dto.response.NotificationResponse;
import iuh.fit.fashionwebsite.dto.response.StockUpdateResponse;
import iuh.fit.fashionwebsite.entity.ChatMessage;
import iuh.fit.fashionwebsite.entity.Notification;
import iuh.fit.fashionwebsite.enums.ChatRole;
import iuh.fit.fashionwebsite.enums.NotificationType;
import iuh.fit.fashionwebsite.event.OrderStatusChangedEvent;
import iuh.fit.fashionwebsite.event.RealtimeEventListener;
import iuh.fit.fashionwebsite.event.StockChangedEvent;
import iuh.fit.fashionwebsite.mapper.ChatMessageMapper;
import iuh.fit.fashionwebsite.mapper.NotificationMapper;
import iuh.fit.fashionwebsite.repository.ChatMessageRepository;
import iuh.fit.fashionwebsite.repository.NotificationRepository;
import iuh.fit.fashionwebsite.repository.UserRepository;
import iuh.fit.fashionwebsite.security.WebSocketAuthInterceptor;
import iuh.fit.fashionwebsite.service.chat.ChatServiceImpl;
import iuh.fit.fashionwebsite.service.notification.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAndRealtimeTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private MessageChannel messageChannel;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketAuthInterceptor authInterceptor;
    private NotificationServiceImpl notificationService;
    private ChatServiceImpl chatService;
    private ChatWsController chatWsController;
    private RealtimeEventListener eventListener;

    @BeforeEach
    void setUp() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        authInterceptor = new WebSocketAuthInterceptor(jwtDecoder, jwtAuthConverter);
        notificationService = new NotificationServiceImpl(notificationRepository, notificationMapper, messagingTemplate);
        chatService = new ChatServiceImpl(chatMessageRepository, userRepository, chatMessageMapper, messagingTemplate);
        chatWsController = new ChatWsController(chatService);
        eventListener = new RealtimeEventListener(notificationService, messagingTemplate, userRepository);
    }

    @Test
    void connectWithoutToken_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessagingException ex = assertThrows(MessagingException.class,
                () -> authInterceptor.preSend(msg, messageChannel));
        assertEquals("Thiếu token", ex.getMessage());
    }

    @Test
    void connectWithInvalidToken_throwsMessagingException() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("Invalid"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessagingException ex = assertThrows(MessagingException.class,
                () -> authInterceptor.preSend(msg, messageChannel));
        assertEquals("Token không hợp lệ", ex.getMessage());
    }

    @Test
    void connectWithValidToken_setsAuthenticatedPrincipal() {
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "HS512")
                .subject("truong01")
                .claim("scope", "CUSTOMER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        authInterceptor.preSend(msg, messageChannel);
        assertNotNull(accessor.getUser());
        assertEquals("truong01", accessor.getUser().getName());
    }

    @Test
    void customerSubscribeToAdminTopic_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/chat");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "truong01", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessagingException ex = assertThrows(MessagingException.class,
                () -> authInterceptor.preSend(msg, messageChannel));
        assertEquals("Không có quyền subscribe", ex.getMessage());
    }

    @Test
    void adminSubscribeToAdminTopic_succeeds() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/chat");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "admin01", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> authInterceptor.preSend(msg, messageChannel));
    }

    @Test
    void orderStatusEvent_pushesRealtimeNotificationToCustomer() {
        Notification saved = Notification.builder()
                .id(10L)
                .username("truong01")
                .type(NotificationType.ORDER_STATUS)
                .title("Đơn hàng #99 đã được cập nhật")
                .content("Trạng thái mới: SHIPPING")
                .referenceId(99L)
                .seen(false)
                .build();
        NotificationResponse dto = new NotificationResponse(
                10L, NotificationType.ORDER_STATUS, saved.getTitle(), saved.getContent(),
                99L, false, LocalDateTime.now());

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationMapper.toResponse(saved)).thenReturn(dto);

        eventListener.onOrderStatus(new OrderStatusChangedEvent(99L, "truong01", "SHIPPING"));

        verify(messagingTemplate).convertAndSendToUser(eq("truong01"), eq("/queue/notifications"), eq(dto));
    }

    @Test
    void stockChangedEvent_broadcastsRealtimeStockToTopic() {
        eventListener.onStock(new StockChangedEvent(5L, 12L, 7));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/products/5/stock"),
                eq(new StockUpdateResponse(5L, 12L, 7))
        );
    }

    @Test
    void chatSendAndReply_deliversToCustomerQueueAndAdminTopic() {
        ChatMessage savedMsg = ChatMessage.builder()
                .id(1L)
                .customerUsername("truong01")
                .senderUsername("truong01")
                .senderRole(ChatRole.CUSTOMER)
                .content("Xin chào shop")
                .build();
        ChatMessageResponse response = new ChatMessageResponse(
                1L, "truong01", "truong01", ChatRole.CUSTOMER, "Xin chào shop", LocalDateTime.now());

        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);
        when(chatMessageMapper.toResponse(savedMsg)).thenReturn(response);

        UsernamePasswordAuthenticationToken customerPrincipal = new UsernamePasswordAuthenticationToken(
                "truong01", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        chatWsController.customerSend(new ChatMessageSendRequest("Xin chào shop"), customerPrincipal);

        verify(messagingTemplate).convertAndSendToUser(eq("truong01"), eq("/queue/chat"), eq(response));
        verify(messagingTemplate).convertAndSend(eq("/topic/admin/chat"), eq(response));

        // Customer trying to call staffReply should be denied
        assertThrows(AccessDeniedException.class, () ->
                chatWsController.staffReply(new ChatMessageReplyRequest("truong01", "Hack"), customerPrincipal));
    }
}
