package martin.game.model;


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
public class User implements UserDetails {
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

    @PrePersist // JPA 生命周期注解：在 persist() 执行前触发（即插入数据库前）
    public void prePersist(){
        // 若未手动设置 createTime，则默认填充当前时间
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
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
