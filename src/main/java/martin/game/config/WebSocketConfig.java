package martin.game.config;

import martin.game.interceptor.WebSocketUserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点，并添加用户信息拦截器
        registry.addEndpoint("/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor()) // 关键：添加拦截器
                .setAllowedOriginPatterns("*")
                .withSockJS();

    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // 广播消息代理
        config.setApplicationDestinationPrefixes("/app"); // 应用消息前缀
    }
}
