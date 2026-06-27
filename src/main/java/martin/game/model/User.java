package martin.game.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "user")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements UserDetails {

    /**
     * 显式 serialVersionUID：避免 JPA 字段变更后自动生成的 UID 改变，
     * 导致 Spring Session 存储的旧 session（JDK 序列化）反序列化失败。
     * 新增字段时此 UID 保持不变，Java 序列化对新增字段默认填充为 null/0，兼容老 session。
     */
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 45)
    private String username;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false, length = 45)
    private String nickname;

    @Column(unique = false, nullable = true, length = 45)
    private String email;

    @Column(name="create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name="total_games")
    private Integer totalGames = 0;

    @Column(name="win_games")
    private Integer winGames = 0;

    @Column(name="score")
    private Integer score = 0;

    @Column(name="iconUrl")
    private String iconUrl = null;

    @Enumerated(EnumType.STRING)
    @Column(name="role", nullable = false, length = 16)
    private Role role = Role.PLAYER;

    @Column(name="vip_expire_at")
    private LocalDateTime vipExpireAt = null;

    @PrePersist // JPA 生命周期注解：在 persist() 执行前触发（即插入数据库前）
    public void prePersist(){
        // 若未手动设置 createTime，则默认填充当前时间
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
        // 默认角色为普通玩家（与 DB DEFAULT 对应）
        if (this.role == null) {
            this.role = Role.PLAYER;
        }
    }

    /**
     * 有效角色：综合 DB 字段与 VIP 过期时间。
     * <ul>
     *     <li>PLAYER / ADMIN 永不过期，直接返回；</li>
     *     <li>VIP / SVIP：若 vipExpireAt 为空或已过，返回 PLAYER；否则返回原角色。</li>
     * </ul>
     */
    public Role getEffectiveRole() {
        return getEffectiveRole(LocalDateTime.now());
    }

    public Role getEffectiveRole(LocalDateTime now) {
        if (this.role == null) {
            return Role.PLAYER;
        }
        if (this.role == Role.PLAYER || this.role == Role.ADMIN) {
            return this.role;
        }
        if (this.vipExpireAt == null || this.vipExpireAt.isBefore(now)) {
            return Role.PLAYER;
        }
        return this.role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //所有用户都只有普通权限
        return Collections.emptyList();
    }

    @Override
    // 判断用户账户是否未过期
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    // 判断用户账户是否未被锁定。
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    // 用于判断用户的凭证（如密码）是否未过期
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    // 判断用户账户是否启用
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return username;
    }
}
