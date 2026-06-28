package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final Logger logger = LogManager.getLogger(PhoneVerificationService.class);
    private static final String BIND_CODE_KEY_PREFIX = "phone-bind:code:";
    private static final String BIND_COOLDOWN_KEY_PREFIX = "phone-bind:cooldown:";
    private static final String RESET_CODE_KEY_PREFIX = "phone-reset:code:";
    private static final String RESET_COOLDOWN_KEY_PREFIX = "phone-reset:cooldown:";
    private static final String PASSWORD_CODE_KEY_PREFIX = "phone-password:code:";
    private static final String PASSWORD_COOLDOWN_KEY_PREFIX = "phone-password:cooldown:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;

    @Value("${app.phone-verification.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${app.phone-verification.cooldown-seconds:60}")
    private long cooldownSeconds;

    public SendCodeResult sendBindCode(String username, String phoneNumber) {
        String phone = normalizePhone(phoneNumber);
        User user = userService.findByUsername(username);
        if (phone.equals(user.getPhoneNumber())) {
            throw new IllegalArgumentException("该手机号已绑定当前账号");
        }
        if (userService.isPhoneBoundToOtherUser(username, phone)) {
            throw new IllegalArgumentException("该手机号已绑定其他账号");
        }
        return sendCode(BIND_CODE_KEY_PREFIX, BIND_COOLDOWN_KEY_PREFIX, username, phone, "bind");
    }

    public void bindPhone(String username, String phoneNumber, String code) {
        String phone = normalizePhone(phoneNumber);
        verifyCode(BIND_CODE_KEY_PREFIX, username, phone, code);
        if (userService.isPhoneBoundToOtherUser(username, phone)) {
            throw new IllegalArgumentException("该手机号已绑定其他账号");
        }
        if (!userService.updatePhone(username, phone)) {
            throw new IllegalStateException("绑定手机号失败，请稍后重试");
        }

        redisTemplate.delete(codeKey(BIND_CODE_KEY_PREFIX, username));
        redisTemplate.delete(BIND_COOLDOWN_KEY_PREFIX + username);
    }

    public SendCodeResult sendPasswordResetCode(String username, String phoneNumber) {
        String account = normalizeUsername(username);
        String phone = normalizePhone(phoneNumber);
        User user = userService.findByUsername(account);
        if (user.getPhoneNumber() == null || !phone.equals(user.getPhoneNumber())) {
            throw new IllegalArgumentException("用户名与绑定手机号不匹配");
        }
        return sendCode(RESET_CODE_KEY_PREFIX, RESET_COOLDOWN_KEY_PREFIX, account, phone, "password-reset");
    }

    public void resetPassword(String username, String phoneNumber, String code, String newPassword) {
        String account = normalizeUsername(username);
        String phone = normalizePhone(phoneNumber);
        User user = userService.findByUsername(account);
        if (user.getPhoneNumber() == null || !phone.equals(user.getPhoneNumber())) {
            throw new IllegalArgumentException("用户名与绑定手机号不匹配");
        }
        verifyCode(RESET_CODE_KEY_PREFIX, account, phone, code);
        updatePassword(account, newPassword);
        redisTemplate.delete(codeKey(RESET_CODE_KEY_PREFIX, account));
        redisTemplate.delete(RESET_COOLDOWN_KEY_PREFIX + account);
    }

    public SendCodeResult sendPasswordChangeCode(String username) {
        User user = userService.findByUsername(username);
        String phone = normalizeBoundPhone(user.getPhoneNumber());
        return sendCode(PASSWORD_CODE_KEY_PREFIX, PASSWORD_COOLDOWN_KEY_PREFIX, username, phone, "password-change");
    }

    public void changePassword(String username, String code, String newPassword) {
        User user = userService.findByUsername(username);
        String phone = normalizeBoundPhone(user.getPhoneNumber());
        verifyCode(PASSWORD_CODE_KEY_PREFIX, username, phone, code);
        updatePassword(username, newPassword);
        redisTemplate.delete(codeKey(PASSWORD_CODE_KEY_PREFIX, username));
        redisTemplate.delete(PASSWORD_COOLDOWN_KEY_PREFIX + username);
    }

    private SendCodeResult sendCode(String codePrefix, String cooldownPrefix,
                                    String username, String phone, String scene) {
        String cooldownKey = cooldownPrefix + username;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, phone, Duration.ofSeconds(cooldownSeconds));
        if (Boolean.FALSE.equals(acquired)) {
            throw new IllegalStateException("验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey(codePrefix, username),
                phone + ":" + code,
                Duration.ofMinutes(ttlMinutes));
        logger.info("Phone verification code: scene={}, user={}, phone={}, code={}, ttlMinutes={}",
                scene, username, maskPhone(phone), code, ttlMinutes);
        return new SendCodeResult(phone, maskPhone(phone), code, ttlMinutes, cooldownSeconds);
    }

    private void verifyCode(String codePrefix, String username, String phoneNumber, String code) {
        String phone = normalizePhone(phoneNumber);
        String normalizedCode = normalizeCode(code);
        String value = redisTemplate.opsForValue().get(codeKey(codePrefix, username));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }

        String[] parts = value.split(":", 2);
        if (parts.length != 2 || !phone.equals(parts[0]) || !normalizedCode.equals(parts[1])) {
            throw new IllegalArgumentException("验证码错误");
        }
    }

    private void updatePassword(String username, String newPassword) {
        if (!userService.updatePassword(username, newPassword)) {
            throw new IllegalStateException("密码更新失败，请稍后重试");
        }
    }

    private String codeKey(String prefix, String username) {
        return prefix + username;
    }

    private String normalizeUsername(String username) {
        String normalized = Optional.ofNullable(username).orElse("").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("请输入用户名");
        }
        return normalized;
    }

    private String normalizeBoundPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("当前账号未绑定手机号，请先绑定后再操作");
        }
        return normalizePhone(phoneNumber);
    }

    private String normalizePhone(String phoneNumber) {
        String phone = Optional.ofNullable(phoneNumber).orElse("").trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("请输入有效的 11 位手机号");
        }
        return phone;
    }

    private String normalizeCode(String code) {
        String normalized = Optional.ofNullable(code).orElse("").trim();
        if (!normalized.matches("^\\d{6}$")) {
            throw new IllegalArgumentException("请输入 6 位数字验证码");
        }
        return normalized;
    }

    private String maskPhone(String phone) {
        if (phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    public static class SendCodeResult {
        private final String phoneNumber;
        private final String maskedPhoneNumber;
        private final String code;
        private final long ttlMinutes;
        private final long cooldownSeconds;

        public SendCodeResult(String phoneNumber, String maskedPhoneNumber,
                              String code, long ttlMinutes, long cooldownSeconds) {
            this.phoneNumber = phoneNumber;
            this.maskedPhoneNumber = maskedPhoneNumber;
            this.code = code;
            this.ttlMinutes = ttlMinutes;
            this.cooldownSeconds = cooldownSeconds;
        }

        public String phoneNumber() {
            return phoneNumber;
        }

        public String maskedPhoneNumber() {
            return maskedPhoneNumber;
        }

        public String code() {
            return code;
        }

        public long ttlMinutes() {
            return ttlMinutes;
        }

        public long cooldownSeconds() {
            return cooldownSeconds;
        }
    }
}
