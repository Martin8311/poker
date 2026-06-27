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
    private static final String CODE_KEY_PREFIX = "phone-bind:code:";
    private static final String COOLDOWN_KEY_PREFIX = "phone-bind:cooldown:";
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

        String cooldownKey = COOLDOWN_KEY_PREFIX + username;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, phone, Duration.ofSeconds(cooldownSeconds));
        if (Boolean.FALSE.equals(acquired)) {
            throw new IllegalStateException("验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(codeKey(username), phone + ":" + code, Duration.ofMinutes(ttlMinutes));
        logger.info("Phone bind verification code: user={}, phone={}, code={}, ttlMinutes={}",
                username, maskPhone(phone), code, ttlMinutes);
        return new SendCodeResult(phone, code, ttlMinutes, cooldownSeconds);
    }

    public void bindPhone(String username, String phoneNumber, String code) {
        String phone = normalizePhone(phoneNumber);
        String normalizedCode = normalizeCode(code);
        String value = redisTemplate.opsForValue().get(codeKey(username));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }

        String[] parts = value.split(":", 2);
        if (parts.length != 2 || !phone.equals(parts[0]) || !normalizedCode.equals(parts[1])) {
            throw new IllegalArgumentException("验证码错误");
        }
        if (userService.isPhoneBoundToOtherUser(username, phone)) {
            throw new IllegalArgumentException("该手机号已绑定其他账号");
        }
        if (!userService.updatePhone(username, phone)) {
            throw new IllegalStateException("绑定手机号失败，请稍后重试");
        }

        redisTemplate.delete(codeKey(username));
        redisTemplate.delete(COOLDOWN_KEY_PREFIX + username);
    }

    private String codeKey(String username) {
        return CODE_KEY_PREFIX + username;
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
        private final String code;
        private final long ttlMinutes;
        private final long cooldownSeconds;

        public SendCodeResult(String phoneNumber, String code, long ttlMinutes, long cooldownSeconds) {
            this.phoneNumber = phoneNumber;
            this.code = code;
            this.ttlMinutes = ttlMinutes;
            this.cooldownSeconds = cooldownSeconds;
        }

        public String phoneNumber() {
            return phoneNumber;
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
