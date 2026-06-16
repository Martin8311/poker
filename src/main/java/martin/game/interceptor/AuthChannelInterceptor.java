package martin.game.interceptor;

import martin.game.service.RoomService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP 消息级鉴权拦截器（入站通道）。
 *
 * <p>WebSocket 不像 HTTP 那样每个请求都过 Security Filter，连接建立后的每一条 STOMP 消息
 * 默认无人校验。该拦截器在消息进入业务前做统一鉴权：
 * <ul>
 *   <li>CONNECT / SEND / SUBSCRIBE 必须已认证（user 由握手时的 Spring Security 身份提供，客户端无法伪造）；</li>
 *   <li>SUBSCRIBE {@code /topic/rooms.{roomId}} 时校验当前用户确为该房间成员，杜绝「偷听任意房间」。</li>
 * </ul>
 */
@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms.";

    private final RoomService roomService;
    private static final Logger logger = LogManager.getLogger(AuthChannelInterceptor.class);

    public AuthChannelInterceptor(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message; // 心跳 / SockJS 内部帧等无命令帧，放行
        }

        switch (command) {
            case CONNECT:
            case SEND:
                requireAuthenticated(accessor);
                break;
            case SUBSCRIBE:
                requireAuthenticated(accessor);
                checkRoomSubscription(accessor);
                break;
            default:
                // DISCONNECT / UNSUBSCRIBE / ACK 等放行
                break;
        }
        return message;
    }

    /** 要求消息已携带认证身份，否则拒绝投递。 */
    private void requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            logger.warn("拒绝未认证的 STOMP {} 消息", accessor.getCommand());
            throw new MessageDeliveryException("未认证，拒绝该消息");
        }
    }

    /** 订阅房间话题时，校验当前用户是该房间成员。 */
    private void checkRoomSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return; // 非房间话题不做成员校验
        }
        String roomId = destination.substring(ROOM_TOPIC_PREFIX.length());
        Principal user = accessor.getUser();
        if (!roomService.isUserInRoom(roomId, user.getName())) {
            logger.warn("用户 {} 试图订阅非本人所在房间 {}，已拒绝", user.getName(), roomId);
            throw new MessageDeliveryException("无权订阅该房间");
        }
    }
}
