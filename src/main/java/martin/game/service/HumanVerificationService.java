package martin.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HumanVerificationService {

    private static final String CHALLENGE_KEY_PREFIX = "human-verify:challenge:";
    private static final String PASSED_KEY_PREFIX = "human-verify:passed:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    @Value("${app.human-verification.challenge-ttl-seconds:180}")
    private long challengeTtlSeconds;

    @Value("${app.human-verification.passed-ttl-seconds:120}")
    private long passedTtlSeconds;

    @Value("${app.human-verification.slider-tolerance:8}")
    private int sliderTolerance;

    public Challenge createChallenge(String username) {
        String challengeId = UUID.randomUUID().toString();
        if (RANDOM.nextBoolean()) {
            return createImageChallenge(challengeId, username);
        }
        return createSliderChallenge(challengeId, username);
    }

    public VerificationResult verify(String username, String challengeId, String answer) {
        String id = normalize(challengeId);
        String normalizedAnswer = normalize(answer);
        String value = redisTemplate.opsForValue().get(CHALLENGE_KEY_PREFIX + id);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("人机验证码已过期，请刷新后重试");
        }

        String[] parts = value.split(":", 4);
        if (parts.length != 4 || !username.equals(parts[0])) {
            throw new IllegalArgumentException("人机验证码无效，请刷新后重试");
        }

        boolean passed = false;
        if ("IMAGE".equals(parts[1])) {
            passed = normalizedAnswer.matches("\\d+") && normalizedAnswer.equals(parts[2]);
        } else if ("SLIDER".equals(parts[1]) && normalizedAnswer.matches("\\d+")) {
            passed = Math.abs(Integer.parseInt(normalizedAnswer) - Integer.parseInt(parts[2]))
                    <= Integer.parseInt(parts[3]);
        }

        if (!passed) {
            throw new IllegalArgumentException("人机验证码错误，请重试");
        }

        redisTemplate.delete(CHALLENGE_KEY_PREFIX + id);
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PASSED_KEY_PREFIX + token, username, Duration.ofSeconds(passedTtlSeconds));
        return new VerificationResult(token, passedTtlSeconds);
    }

    public void consumePassedToken(String username, String token) {
        String normalizedToken = normalize(token);
        if (normalizedToken.isBlank()) {
            throw new IllegalArgumentException("请先完成人机验证");
        }

        String key = PASSED_KEY_PREFIX + normalizedToken;
        String owner = redisTemplate.opsForValue().get(key);
        if (!username.equals(owner)) {
            throw new IllegalArgumentException("人机验证已失效，请重新验证");
        }
        redisTemplate.delete(key);
    }

    private Challenge createImageChallenge(String challengeId, String username) {
        int a = 10 + RANDOM.nextInt(40);
        int b = 1 + RANDOM.nextInt(30);
        boolean plus = RANDOM.nextBoolean();
        int answer = plus ? a + b : a - b;
        String expression = a + (plus ? " + " : " - ") + b + " = ?";
        redisTemplate.opsForValue().set(CHALLENGE_KEY_PREFIX + challengeId,
                username + ":IMAGE:" + answer + ":0",
                Duration.ofSeconds(challengeTtlSeconds));
        return new Challenge(challengeId, "IMAGE", "请输入图中算式结果", expression,
                null, null, null, challengeTtlSeconds);
    }

    private Challenge createSliderChallenge(String challengeId, String username) {
        int trackWidth = 260;
        int targetX = 48 + RANDOM.nextInt(160);
        redisTemplate.opsForValue().set(CHALLENGE_KEY_PREFIX + challengeId,
                username + ":SLIDER:" + targetX + ":" + sliderTolerance,
                Duration.ofSeconds(challengeTtlSeconds));
        return new Challenge(challengeId, "SLIDER", "拖动滑块到缺口位置", null,
                trackWidth, targetX, sliderTolerance, challengeTtlSeconds);
    }

    private String normalize(String value) {
        return Optional.ofNullable(value).orElse("").trim();
    }

    public static class Challenge {
        private final String challengeId;
        private final String type;
        private final String prompt;
        private final String expression;
        private final Integer trackWidth;
        private final Integer targetX;
        private final Integer tolerance;
        private final long ttlSeconds;

        public Challenge(String challengeId, String type, String prompt, String expression,
                         Integer trackWidth, Integer targetX, Integer tolerance, long ttlSeconds) {
            this.challengeId = challengeId;
            this.type = type;
            this.prompt = prompt;
            this.expression = expression;
            this.trackWidth = trackWidth;
            this.targetX = targetX;
            this.tolerance = tolerance;
            this.ttlSeconds = ttlSeconds;
        }

        public String challengeId() {
            return challengeId;
        }

        public String getChallengeId() {
            return challengeId;
        }

        public String type() {
            return type;
        }

        public String getType() {
            return type;
        }

        public String prompt() {
            return prompt;
        }

        public String getPrompt() {
            return prompt;
        }

        public String expression() {
            return expression;
        }

        public String getExpression() {
            return expression;
        }

        public Integer trackWidth() {
            return trackWidth;
        }

        public Integer getTrackWidth() {
            return trackWidth;
        }

        public Integer targetX() {
            return targetX;
        }

        public Integer getTargetX() {
            return targetX;
        }

        public Integer tolerance() {
            return tolerance;
        }

        public Integer getTolerance() {
            return tolerance;
        }

        public long ttlSeconds() {
            return ttlSeconds;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }
    }

    public static class VerificationResult {
        private final String token;
        private final long ttlSeconds;

        public VerificationResult(String token, long ttlSeconds) {
            this.token = token;
            this.ttlSeconds = ttlSeconds;
        }

        public String token() {
            return token;
        }

        public String getToken() {
            return token;
        }

        public long ttlSeconds() {
            return ttlSeconds;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }
    }
}
