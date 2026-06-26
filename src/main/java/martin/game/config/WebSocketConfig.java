package martin.game.config;

import martin.game.interceptor.AuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 是否启用 RabbitMQ 作为 STOMP Broker Relay；默认 false → 用内存版 SimpleBroker（开发无需外部 MQ）
    @Value("${app.websocket.relay.enabled:false}")
    private boolean relayEnabled;
    @Value("${app.websocket.relay.host:localhost}")
    private String relayHost;
    @Value("${app.websocket.relay.port:61613}")
    private int relayPort;
    @Value("${app.websocket.relay.user:guest}")
    private String relayUser;
    @Value("${app.websocket.relay.pass:guest}")
    private String relayPass;

    // 允许的跨域来源（CORS），默认 * 便于本地多端口联调，生产应收敛为具体域名
    @Value("${app.websocket.allowed-origins:*}")
    private String[] allowedOrigins;

    // 入站消息鉴权拦截器（消息级鉴权）
    private final AuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(AuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点（SockJS 兜底），握手时同步 HTTP 会话用户身份
        registry.addEndpoint("/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    /**
     * 注册入站消息拦截器，对每条 STOMP 消息做消息级鉴权
     * （未认证拒绝；订阅房间话题校验成员资格）。
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app"); // 客户端发送前缀

        if (relayEnabled) {
            // 使用 RabbitMQ 作为外部 STOMP 消息代理（Broker Relay）：
            // 多个应用实例连同一个 broker，可跨实例广播 → 支持 WebSocket 水平扩展
            config.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayUser)
                    .setClientPasscode(relayPass)
                    .setSystemLogin(relayUser)
                    .setSystemPasscode(relayPass)
                    // 用户级目的地（convertAndSendToUser -> /user/queue/...）跨实例广播：
                    // 申请者与接收者可能连在不同实例，靠这两个 broker 话题同步用户会话注册与未解析的用户消息
                    .setUserRegistryBroadcast("/topic/poker.user-registry")
                    .setUserDestinationBroadcast("/topic/poker.user-dest");
        } else {
            // 默认：内存版 SimpleBroker（单机，开发无需外部 MQ）
            config.enableSimpleBroker("/topic");
        }
    }
}
