package martin.game.service;

import martin.game.model.Role;
import martin.game.model.User;
import martin.game.repository.UserRepository;
import martin.game.utils.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceBanTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeaderboardService leaderboardService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, leaderboardService);
    }

    @Test
    @DisplayName("封禁普通用户时写入原因和时间")
    void banUserWritesReasonAndTime() {
        User target = user("alice", Role.PLAYER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));
        when(userRepository.banByUsername(eq("alice"), eq("spam"), any(LocalDateTime.class), isNull())).thenReturn(1);

        boolean result = userService.banUser("admin", " alice ", " spam ");

        assertThat(result).isTrue();
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).banByUsername(eq("alice"), eq("spam"), timeCaptor.capture(), isNull());
        assertThat(timeCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("临时封禁会写入到期时间")
    void temporaryBanWritesExpireTime() {
        User target = user("alice", Role.PLAYER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));
        when(userRepository.banByUsername(eq("alice"), eq("spam"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        boolean result = userService.banUser("admin", "alice", "spam", 2, "HOURS");

        assertThat(result).isTrue();
        ArgumentCaptor<LocalDateTime> expireCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).banByUsername(eq("alice"), eq("spam"), any(LocalDateTime.class), expireCaptor.capture());
        assertThat(expireCaptor.getValue()).isAfter(LocalDateTime.now().plusMinutes(119));
    }

    @Test
    @DisplayName("封禁原因为空时使用默认原因")
    void banUserUsesDefaultReason() {
        User target = user("alice", Role.PLAYER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));
        when(userRepository.banByUsername(eq("alice"), eq("管理员封禁"), any(LocalDateTime.class), isNull())).thenReturn(1);

        assertThat(userService.banUser("admin", "alice", "  ")).isTrue();

        verify(userRepository).banByUsername(eq("alice"), eq("管理员封禁"), any(LocalDateTime.class), isNull());
    }

    @Test
    @DisplayName("不能封禁自己")
    void cannotBanSelf() {
        assertThatThrownBy(() -> userService.banUser("admin", " admin ", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不能封禁自己");

        verify(userRepository, never()).banByUsername(any(), any(), any(), any());
    }

    @Test
    @DisplayName("不能封禁管理员账号")
    void cannotBanAdmin() {
        User target = user("root", Role.ADMIN);
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.banUser("admin", "root", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不能封禁管理员账号");

        verify(userRepository, never()).banByUsername(any(), any(), any(), any());
    }

    @Test
    @DisplayName("解封会清理封禁状态")
    void unbanUserClearsBanState() {
        User target = user("alice", Role.PLAYER);
        target.setBanned(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));
        when(userRepository.unbanByUsername("alice")).thenReturn(1);

        assertThat(userService.unbanUser(" alice ")).isTrue();

        verify(userRepository).unbanByUsername("alice");
    }

    @Test
    @DisplayName("LoginUser 继承封禁状态")
    void loginUserReflectsBanState() {
        User target = user("alice", Role.PLAYER);
        target.setBanned(true);

        LoginUser loginUser = new LoginUser(target);

        assertThat(loginUser.isAccountNonLocked()).isFalse();
        assertThat(loginUser.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("过期封禁会自动清理")
    void expiredBanIsCleared() {
        User target = user("alice", Role.PLAYER);
        target.setBanned(true);
        target.setBanExpireAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));
        when(userRepository.unbanByUsername("alice")).thenReturn(1);

        assertThat(userService.isBanned("alice")).isFalse();

        verify(userRepository).unbanByUsername("alice");
    }

    @Test
    @DisplayName("封禁提示包含原因和时长")
    void activeBanMessageContainsReasonAndDuration() {
        User target = user("alice", Role.PLAYER);
        target.setBanned(true);
        target.setBanReason("spam");
        target.setBanExpireAt(LocalDateTime.of(2030, 1, 2, 3, 4));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));

        assertThat(userService.getActiveBanMessage("alice"))
                .contains("账号已被封禁")
                .contains("原因：spam")
                .contains("封禁时长：至 2030-01-02 03:04");
    }

    @Test
    @DisplayName("永久封禁提示显示永久")
    void permanentBanMessageShowsPermanent() {
        User target = user("alice", Role.PLAYER);
        target.setBanned(true);
        target.setBanReason("spam");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(target));

        assertThat(userService.getActiveBanMessage("alice"))
                .contains("原因：spam")
                .contains("封禁时长：永久");
    }

    @Test
    @DisplayName("后台用户搜索按用户名或昵称模糊查询")
    void findAdminUsersSearchesUsernameOrNickname() {
        User target = user("alice", Role.PLAYER);
        when(userRepository.searchByUsernameOrNickname("ali")).thenReturn(List.of(target));

        assertThat(userService.findAdminUsers(" ali ")).containsExactly(target);

        verify(userRepository).searchByUsernameOrNickname("ali");
    }

    private User user(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setNickname(username);
        user.setRole(role);
        user.setBanned(false);
        return user;
    }
}
