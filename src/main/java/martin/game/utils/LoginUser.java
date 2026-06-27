package martin.game.utils;

import martin.game.model.Role;
import martin.game.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class LoginUser implements UserDetails {

    /**
     * 显式 serialVersionUID：与 {@link martin.game.model.User} 同样的考量，
     * 防止 Spring Session（JDK 序列化）中缓存的 SecurityContext 反序列化失败。
     */
    private static final long serialVersionUID = 1L;

    // 存储数据库中的用户实体（假设你的用户实体类是 User）
    private final User user;

    // 构造方法：传入数据库查询到的 User 实体
    public LoginUser(User user) {
        this.user = user;
    }

    // 新增：获取用户 ID 的方法（核心）
    public Integer getUserId() {
        return user.getId(); // 假设 User 实体有 getId() 方法，返回 Long 类型的 ID
    }

    /**
     * 角色权限集合。Spring Security 6 的 {@code DaoAuthenticationProvider}
     * 会从此处读权限并放入 {@code Authentication#getAuthorities()}。
     * 角色名以 {@code ROLE_} 前缀暴露，匹配 {@code hasRole("ADMIN")} / {@code hasAuthority("ROLE_VIP")}。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Role effective = user.getEffectiveRole();
        return List.of(new SimpleGrantedAuthority(effective.asAuthority()));
    }

    @Override
    public String getPassword() {
        return user.getPassword(); // 返回密码（用于认证）
    }

    @Override
    public String getUsername() {
        return user.getUsername(); // 返回用户名（用于认证）
    }

    // 以下方法默认返回 true（表示账号状态正常，可根据业务调整）
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public User getUser(){
        return user;
    }
}

