package martin.game.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import martin.game.service.UserService;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String LOGIN_ERROR_SESSION_KEY = "LOGIN_ERROR";

    private final UserService userService;

    public LoginFailureHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String message = "用户名或密码错误";
        if (exception instanceof LockedException || exception instanceof DisabledException) {
            String username = request.getParameter("username");
            message = resolveBanMessage(username);
        }
        request.getSession(true).setAttribute(LOGIN_ERROR_SESSION_KEY, message);
        response.sendRedirect(request.getContextPath() + "/login?error");
    }

    private String resolveBanMessage(String username) {
        if (username == null || username.isBlank()) {
            return "账号已被封禁，请联系管理员";
        }
        try {
            String banMessage = userService.getActiveBanMessage(username.trim());
            return banMessage == null ? "账号已被封禁，请联系管理员" : banMessage;
        } catch (UsernameNotFoundException ignored) {
            return "用户名或密码错误";
        }
    }
}
