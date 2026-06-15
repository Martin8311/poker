package martin.game.interceptor;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class WebSocketUserInterceptor implements HandshakeInterceptor {

    // 建立连接前：将HTTP会话中的用户信息同步到WebSocket会话
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 判断请求是否来自Servlet（即HTTP请求升级而来）
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpSession httpSession = servletRequest.getServletRequest().getSession(false); // 不创建新会话

            // 如果HTTP会话存在且包含currentUser，同步到WebSocket会话的属性中
            if (httpSession != null && httpSession.getAttribute("currentUser") != null) {
                attributes.put("currentUser", httpSession.getAttribute("currentUser"));
            }
        }
        return true; // 继续握手流程
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后无需处理
    }
}
