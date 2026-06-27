package martin.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用户角色枚举。
 *
 * <p>存储于 {@code user.role} 列（{@code @Enumerated(EnumType.STRING)}），
 * 优先级由 {@link #level} 决定；ADMIN 永不过期，VIP/SVIP 受 {@code vip_expire_at} 约束。</p>
 *
 * <p>Spring Security 鉴权时通过 {@code ROLE_<name>} 关联，
 * 如 {@code hasRole("ADMIN")}、{@code hasAuthority("ROLE_VIP")}。</p>
 */
public enum Role {

    PLAYER(0, "普通玩家", "role-player"),
    VIP   (1, "VIP",      "role-vip"),
    SVIP  (2, "超级 VIP", "role-svip"),
    ADMIN (3, "管理员",   "role-admin");

    private final int level;
    private final String displayName;
    private final String cssClass;

    Role(int level, String displayName, String cssClass) {
        this.level = level;
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCssClass() {
        return cssClass;
    }

    /**
     * 等级序数比较：当前角色是否不低于 other。
     * 例如 {@code role.isAtLeast(Role.VIP)} 表示 VIP/SVIP/ADMIN 都满足。
     */
    public boolean isAtLeast(Role other) {
        return this.level >= other.level;
    }

    /** Spring Security 权限字符串（如 {@code ROLE_VIP}） */
    public String asAuthority() {
        return "ROLE_" + name();
    }

    /** JSON 序列化时输出枚举名（默认即此行为，显式声明更稳） */
    @JsonValue
    public String json() {
        return name();
    }

    /** 兼容前端可能传入的小写形式 */
    @JsonCreator
    public static Role fromJson(String value) {
        if (value == null) {
            return PLAYER;
        }
        return Role.valueOf(value.toUpperCase());
    }
}
