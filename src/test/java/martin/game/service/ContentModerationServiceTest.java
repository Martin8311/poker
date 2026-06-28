package martin.game.service;

import martin.game.model.ModerationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentModerationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private UserService userService;

    @TempDir
    private Path tempDir;

    private ContentModerationService service;

    @BeforeEach
    void setUp() {
        service = new ContentModerationService(redisTemplate, userService);
        ReflectionTestUtils.setField(service, "maxLength", 120);
        ReflectionTestUtils.setField(service, "rateWindowSeconds", 3L);
        ReflectionTestUtils.setField(service, "rateMaxMessages", 3L);
        ReflectionTestUtils.setField(service, "duplicateWindowSeconds", 10L);
        ReflectionTestUtils.setField(service, "violationWindowSeconds", 60L);
        ReflectionTestUtils.setField(service, "violationMuteThreshold", 3L);
        ReflectionTestUtils.setField(service, "muteSeconds", 300L);
        ReflectionTestUtils.setField(service, "wordsDir", tempDir.toString());
        service.loadSensitiveWords();
    }

    @Test
    @DisplayName("正常聊天内容放行")
    void normalTextPasses() {
        allowRedisChecks(1L);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "大家快点出牌");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getSanitizedText()).isEqualTo("大家快点出牌");
        assertThat(result.isMasked()).isFalse();
    }

    @Test
    @DisplayName("轻度敏感词脱敏后允许发送")
    void maskWordIsSanitized() {
        allowRedisChecks(1L);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "你 丫 的牌真好");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getSanitizedText()).contains("*");
        assertThat(result.isMasked()).isTrue();
    }

    @Test
    @DisplayName("严重违规词直接拒绝")
    void rejectWordIsBlocked() {
        allowRedisChecks(1L);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(false);
        when(valueOps.increment(anyString())).thenReturn(1L);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "这里有外挂");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getReason()).isEqualTo("消息包含违规内容，请修改后发送");
        verify(valueOps).increment(org.mockito.ArgumentMatchers.startsWith("chat:violation:alice:"));
    }

    @Test
    @DisplayName("联系方式信息直接拒绝")
    void contactInfoIsBlocked() {
        allowRedisChecks(1L);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(false);
        when(valueOps.increment(anyString())).thenReturn(1L);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "加我微信 vxabc12345");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getReason()).isEqualTo("消息包含联系方式或广告信息，请修改后发送");
    }

    @Test
    @DisplayName("短时间重复消息会被拒绝")
    void duplicateMessageIsBlocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("chat:mute:alice")).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(10)))).thenReturn(false);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "快点");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getReason()).isEqualTo("请不要重复发送相同内容");
        verify(valueOps, never()).increment(org.mockito.ArgumentMatchers.startsWith("chat:rate:room1:alice:"));
    }

    @Test
    @DisplayName("超出频率限制时拒绝发送")
    void rateLimitBlocks() {
        allowRedisChecks(4L);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "快点");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getReason()).isEqualTo("消息发送过于频繁，请稍后再试");
    }

    @Test
    @DisplayName("杈惧埌杩濊闃堝€煎悗鍔犲叆绂佽█鍒楄〃")
    void repeatedViolationsMuteUser() {
        allowRedisChecks(1L);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(false);
        when(valueOps.increment(anyString())).thenReturn(3L);

        ContentModerationService.ModerationResult result =
                service.moderateRoomChat("room1", "alice", "vxabc12345");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getAction()).isEqualTo(ModerationAction.REJECT);
        verify(valueOps).set("chat:mute:alice", "1", Duration.ofSeconds(300));
        verify(setOps).add("chat:mute-users", "alice");
    }

    @Test
    @DisplayName("鍚庡彴鍙互瑙ｉ櫎绂佽█")
    void unmuteRemovesMuteKeys() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        service.unmute("alice");

        verify(redisTemplate).delete("chat:mute:alice");
        verify(setOps).remove("chat:mute-users", "alice");
    }

    @Test
    @DisplayName("昵称命中脱敏词时拒绝提交")
    void nicknameRejectsMaskWords() {
        ContentModerationService.ModerationResult result =
                service.moderateNickname("alice", "你 丫");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getAction()).isEqualTo(ModerationAction.REJECT);
        assertThat(result.getReason()).contains("昵称");
    }

    @Test
    @DisplayName("房间简介命中脱敏词时替换后通过")
    void roomDescriptionMasksSensitiveWords() {
        ContentModerationService.ModerationResult result =
                service.moderateRoomDescription("alice", "你 丫 的房间");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isMasked()).isTrue();
        assertThat(result.getSanitizedText()).contains("*");
    }

    @Test
    @DisplayName("私信命中联系方式时拦截")
    void privateMessageRejectsContactInfo() {
        allowRedisChecks(1L);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(false);
        when(valueOps.increment(anyString())).thenReturn(1L);

        ContentModerationService.ModerationResult result =
                service.moderatePrivateMessage("alice:bob", "alice", "加我微信 vxabc12345");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getAction()).isEqualTo(ModerationAction.REJECT);
        assertThat(result.getReason()).contains("消息");
    }

    private void allowRedisChecks(long rateCount) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("chat:mute:alice")).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(10)))).thenReturn(true);
        if (rateCount == 1L) {
            when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(3)))).thenReturn(true);
        } else {
            when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(3)))).thenReturn(false);
            when(valueOps.increment(org.mockito.ArgumentMatchers.startsWith("chat:rate:room1:alice:")))
                    .thenReturn(rateCount);
        }
    }
}
