package martin.game.utils;

import martin.game.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class LoginUser implements UserDetails {

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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 返回用户权限（根据实际业务实现，这里简化为返回空集合）
        return null;
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

