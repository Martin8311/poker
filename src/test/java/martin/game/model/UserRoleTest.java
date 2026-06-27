package martin.game.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    @DisplayName("VIP 未过期时保留有效角色")
    void activeVipRemainsVip() {
        User user = new User();
        user.setRole(Role.VIP);
        user.setVipExpireAt(LocalDateTime.of(2030, 1, 1, 0, 0));

        assertThat(user.getEffectiveRole(LocalDateTime.of(2029, 1, 1, 0, 0)))
                .isEqualTo(Role.VIP);
    }

    @Test
    @DisplayName("VIP 过期后按普通玩家处理")
    void expiredVipFallsBackToPlayer() {
        User user = new User();
        user.setRole(Role.SVIP);
        user.setVipExpireAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        assertThat(user.getEffectiveRole(LocalDateTime.of(2026, 1, 1, 0, 0)))
                .isEqualTo(Role.PLAYER);
    }

    @Test
    @DisplayName("ADMIN 不受 VIP 过期时间影响")
    void adminNeverExpires() {
        User user = new User();
        user.setRole(Role.ADMIN);
        user.setVipExpireAt(LocalDateTime.of(2020, 1, 1, 0, 0));

        assertThat(user.getEffectiveRole(LocalDateTime.of(2030, 1, 1, 0, 0)))
                .isEqualTo(Role.ADMIN);
    }
}
