package martin.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HumanVerificationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private HumanVerificationService service;

    @BeforeEach
    void setUp() {
        service = new HumanVerificationService(redisTemplate);
        ReflectionTestUtils.setField(service, "challengeTtlSeconds", 180L);
        ReflectionTestUtils.setField(service, "passedTtlSeconds", 120L);
        ReflectionTestUtils.setField(service, "sliderTolerance", 8);
    }

    @Test
    @DisplayName("创建人机验证码时写入 Redis 并设置过期时间")
    void createChallengeStoresExpectedAnswerWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        HumanVerificationService.Challenge challenge = service.createChallenge("alice");

        assertThat(challenge.challengeId()).isNotBlank();
        assertThat(challenge.type()).isIn("IMAGE", "SLIDER");
        verify(valueOps).set(eq("human-verify:challenge:" + challenge.challengeId()),
                any(String.class),
                eq(Duration.ofSeconds(180)));
    }

    @Test
    @DisplayName("人机验证码 challenge 可以被 Jackson 序列化给前端")
    void challengeCanBeSerializedToJson() throws Exception {
        HumanVerificationService.Challenge challenge =
                new HumanVerificationService.Challenge("challenge-1", "IMAGE", "请输入图中算式结果",
                        "12 + 3 = ?", null, null, null, 180);

        JsonNode json = new ObjectMapper().valueToTree(challenge);

        assertThat(json.get("challengeId").asText()).isEqualTo("challenge-1");
        assertThat(json.get("type").asText()).isEqualTo("IMAGE");
        assertThat(json.get("prompt").asText()).isEqualTo("请输入图中算式结果");
        assertThat(json.get("ttlSeconds").asLong()).isEqualTo(180);
    }

    @Test
    @DisplayName("图形验证码答案正确时生成一次性通过 token")
    void verifyImageChallengeCreatesPassedToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("human-verify:challenge:challenge-1")).thenReturn("alice:IMAGE:42:0");

        HumanVerificationService.VerificationResult result =
                service.verify("alice", "challenge-1", "42");

        assertThat(result.token()).isNotBlank();
        assertThat(result.ttlSeconds()).isEqualTo(120);
        verify(redisTemplate).delete("human-verify:challenge:challenge-1");
        ArgumentCaptor<String> tokenKey = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(tokenKey.capture(), eq("alice"), eq(Duration.ofSeconds(120)));
        assertThat(tokenKey.getValue()).startsWith("human-verify:passed:");
    }

    @Test
    @DisplayName("滑块验证码允许少量误差")
    void verifySliderChallengeAllowsTolerance() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("human-verify:challenge:challenge-1")).thenReturn("alice:SLIDER:120:8");

        HumanVerificationService.VerificationResult result =
                service.verify("alice", "challenge-1", "127");

        assertThat(result.token()).isNotBlank();
    }

    @Test
    @DisplayName("人机验证 token 只能被所属用户消费")
    void consumePassedTokenChecksOwner() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("human-verify:passed:token-1")).thenReturn("alice");

        service.consumePassedToken("alice", "token-1");

        verify(redisTemplate).delete("human-verify:passed:token-1");
    }

    @Test
    @DisplayName("错误答案会拒绝并不生成通过 token")
    void wrongAnswerRejects() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("human-verify:challenge:challenge-1")).thenReturn("alice:IMAGE:42:0");

        assertThatThrownBy(() -> service.verify("alice", "challenge-1", "41"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("人机验证码错误，请重试");
    }
}
