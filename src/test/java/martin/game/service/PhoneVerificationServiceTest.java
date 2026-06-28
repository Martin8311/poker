package martin.game.service;

import martin.game.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private UserService userService;

    private PhoneVerificationService service;

    @BeforeEach
    void setUp() {
        service = new PhoneVerificationService(redisTemplate, userService);
        ReflectionTestUtils.setField(service, "ttlMinutes", 5L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
    }

    @Test
    @DisplayName("发送绑定验证码时写入冷却 key 和验证码 key")
    void sendBindCodeStoresCodeWithTtl() {
        User user = new User();
        user.setUsername("alice");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(userService.isPhoneBoundToOtherUser("alice", "13812345678")).thenReturn(false);
        when(valueOps.setIfAbsent(eq("phone-bind:cooldown:alice"), eq("13812345678"), any(Duration.class)))
                .thenReturn(true);

        PhoneVerificationService.SendCodeResult result = service.sendBindCode("alice", "13812345678");

        assertThat(result.phoneNumber()).isEqualTo("13812345678");
        assertThat(result.maskedPhoneNumber()).isEqualTo("138****5678");
        assertThat(result.code()).matches("\\d{6}");
        verify(valueOps).set(eq("phone-bind:code:alice"),
                eq("13812345678:" + result.code()),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("验证码正确时绑定手机号并清理 Redis key")
    void bindPhoneWithCorrectCodeUpdatesUser() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("phone-bind:code:alice")).thenReturn("13812345678:123456");
        when(userService.isPhoneBoundToOtherUser("alice", "13812345678")).thenReturn(false);
        when(userService.updatePhone("alice", "13812345678")).thenReturn(true);

        service.bindPhone("alice", "13812345678", "123456");

        verify(userService).updatePhone("alice", "13812345678");
        verify(redisTemplate).delete("phone-bind:code:alice");
        verify(redisTemplate).delete("phone-bind:cooldown:alice");
    }

    @Test
    @DisplayName("验证码不匹配时拒绝绑定")
    void wrongCodeRejectsBind() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("phone-bind:code:alice")).thenReturn("13812345678:123456");

        assertThatThrownBy(() -> service.bindPhone("alice", "13812345678", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码错误");
        verify(userService, never()).updatePhone(any(), any());
    }

    @Test
    @DisplayName("手机号已绑定其他账号时拒绝发送绑定验证码")
    void phoneBoundToOtherUserRejectsSend() {
        User user = new User();
        user.setUsername("alice");
        when(userService.findByUsername("alice")).thenReturn(user);
        when(userService.isPhoneBoundToOtherUser("alice", "13812345678")).thenReturn(true);

        assertThatThrownBy(() -> service.sendBindCode("alice", "13812345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该手机号已绑定其他账号");
        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("找回密码验证码要求用户名和绑定手机号匹配")
    void passwordResetCodeRequiresBoundPhone() {
        User user = new User();
        user.setUsername("alice");
        user.setPhoneNumber("13812345678");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(valueOps.setIfAbsent(eq("phone-reset:cooldown:alice"), eq("13812345678"), any(Duration.class)))
                .thenReturn(true);

        PhoneVerificationService.SendCodeResult result =
                service.sendPasswordResetCode("alice", "13812345678");

        assertThat(result.code()).matches("\\d{6}");
        verify(valueOps).set(eq("phone-reset:code:alice"),
                eq("13812345678:" + result.code()),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("找回密码验证码正确时更新密码")
    void resetPasswordWithCorrectCodeUpdatesPassword() {
        User user = new User();
        user.setUsername("alice");
        user.setPhoneNumber("13812345678");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(valueOps.get("phone-reset:code:alice")).thenReturn("13812345678:123456");
        when(userService.updatePassword("alice", "newpass1")).thenReturn(true);

        service.resetPassword("alice", "13812345678", "123456", "newpass1");

        verify(userService).updatePassword("alice", "newpass1");
        verify(redisTemplate).delete("phone-reset:code:alice");
        verify(redisTemplate).delete("phone-reset:cooldown:alice");
    }

    @Test
    @DisplayName("登录后修改密码验证码发送到当前绑定手机号")
    void sendPasswordChangeCodeUsesCurrentBoundPhone() {
        User user = new User();
        user.setUsername("alice");
        user.setPhoneNumber("13812345678");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(valueOps.setIfAbsent(eq("phone-password:cooldown:alice"), eq("13812345678"), any(Duration.class)))
                .thenReturn(true);

        PhoneVerificationService.SendCodeResult result = service.sendPasswordChangeCode("alice");

        assertThat(result.maskedPhoneNumber()).isEqualTo("138****5678");
        verify(valueOps).set(eq("phone-password:code:alice"),
                eq("13812345678:" + result.code()),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("登录后修改密码验证码正确时更新密码")
    void changePasswordWithCorrectCodeUpdatesPassword() {
        User user = new User();
        user.setUsername("alice");
        user.setPhoneNumber("13812345678");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(valueOps.get("phone-password:code:alice")).thenReturn("13812345678:123456");
        when(userService.updatePassword("alice", "newpass1")).thenReturn(true);

        service.changePassword("alice", "123456", "newpass1");

        verify(userService).updatePassword("alice", "newpass1");
        verify(redisTemplate).delete("phone-password:code:alice");
        verify(redisTemplate).delete("phone-password:cooldown:alice");
    }
}
