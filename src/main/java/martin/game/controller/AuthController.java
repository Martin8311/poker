package martin.game.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import martin.game.config.LoginFailureHandler;
import martin.game.model.User;
import martin.game.service.HumanVerificationService;
import martin.game.service.PhoneVerificationService;
import martin.game.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final Logger logger = LogManager.getLogger(AuthController.class);

    private final UserService userService;
    private final PhoneVerificationService phoneVerificationService;
    private final HumanVerificationService humanVerificationService;

    @Value("${app.phone-verification.debug-code:true}")
    private boolean phoneVerificationDebugCode;

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "banned", required = false) String banned,
                        HttpServletRequest request,
                        Model model) {
        if (error != null) {
            Object loginError = request.getSession().getAttribute(LoginFailureHandler.LOGIN_ERROR_SESSION_KEY);
            request.getSession().removeAttribute(LoginFailureHandler.LOGIN_ERROR_SESSION_KEY);
            model.addAttribute("error", loginError == null ? "用户名或密码错误" : loginError.toString());
        }
        if (logout != null) {
            model.addAttribute("message", "成功登出");
        }
        if (banned != null) {
            model.addAttribute("error", "账号已被封禁，请联系管理员");
        }
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, Model model) {
        try {
            userService.register(user);
            model.addAttribute("message", "注册成功，请登录");
            logger.info("用户 {} 注册账号成功", user.getUsername());
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            logger.error("用户 {} 注册账号失败: {}", user.getUsername(), e.getMessage());
            return "register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @GetMapping("/index")
    public String dashboard() {
        logger.info("登录成功");
        return "hall";
    }

    @PostMapping("/forgot-password/human-verification/challenge")
    @ResponseBody
    public Map<String, Object> createForgotPasswordHumanChallenge(String username) {
        Map<String, Object> result = new HashMap<>();
        try {
            HumanVerificationService.Challenge challenge =
                    humanVerificationService.createChallenge(passwordResetSubject(username));
            result.put("success", true);
            result.put("challenge", challenge);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/forgot-password/human-verification/verify")
    @ResponseBody
    public Map<String, Object> verifyForgotPasswordHuman(String username, String challengeId, String answer) {
        Map<String, Object> result = new HashMap<>();
        try {
            HumanVerificationService.VerificationResult verification =
                    humanVerificationService.verify(passwordResetSubject(username), challengeId, answer);
            result.put("success", true);
            result.put("humanToken", verification.token());
            result.put("ttlSeconds", verification.ttlSeconds());
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/forgot-password/send-code")
    @ResponseBody
    public Map<String, Object> sendForgotPasswordCode(String username, String phoneNumber, String humanToken) {
        Map<String, Object> result = new HashMap<>();
        try {
            String account = normalizeUsername(username);
            humanVerificationService.consumePassedToken(passwordResetSubject(account), humanToken);
            PhoneVerificationService.SendCodeResult sendResult =
                    phoneVerificationService.sendPasswordResetCode(account, phoneNumber);
            result.put("success", true);
            result.put("message", "验证码已发送");
            result.put("maskedPhoneNumber", sendResult.maskedPhoneNumber());
            result.put("ttlMinutes", sendResult.ttlMinutes());
            result.put("cooldownSeconds", sendResult.cooldownSeconds());
            if (phoneVerificationDebugCode) {
                result.put("debugCode", sendResult.code());
            }
        } catch (IllegalArgumentException | IllegalStateException | UsernameNotFoundException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/forgot-password/reset")
    @ResponseBody
    public Map<String, Object> resetForgotPassword(String username, String phoneNumber,
                                                   String code, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        try {
            phoneVerificationService.resetPassword(username, phoneNumber, code, newPassword);
            result.put("success", true);
            result.put("message", "密码已重置，请使用新密码登录");
        } catch (IllegalArgumentException | IllegalStateException | UsernameNotFoundException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private String passwordResetSubject(String username) {
        return "password-reset:" + normalizeUsername(username);
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("请输入用户名");
        }
        return normalized;
    }
}
