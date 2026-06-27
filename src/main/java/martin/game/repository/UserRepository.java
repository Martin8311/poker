package martin.game.repository;

import jakarta.persistence.LockModeType;
import martin.game.model.Role;
import martin.game.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);

    // 批量按用户名查询（排行榜补全昵称 / 头像，避免 N 次查询）
    List<User> findByUsernameIn(Collection<String> usernames);

    // 按昵称模糊搜索好友（忽略大小写）
    List<User> findByNicknameContainingIgnoreCase(String nickname);

    boolean existsByUsername(String username);
    // boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    // 更新积分
    @Modifying
    @Query("UPDATE User p SET p.score = p.score + :update_score, p.winGames = p.winGames + :win, p.totalGames = p.totalGames + 1 WHERE p.username = :username")
    void updateScoreByUsername(@Param("username") String username, @Param("win") int win, @Param("update_score") int update_score);

    // 处理昵称更新.
    @Modifying
    @Query("UPDATE User p SET p.nickname = :nickname WHERE p.username = :username")
    int updateNickNameByUsername(@Param("username") String username, @Param("nickname") String newNickName);

    // 头像上传.
    @Modifying
    @Query("UPDATE User p SET p.iconUrl = :iconUrl WHERE p.username = :username")
    int updateIconUrlByUsername(@Param("username") String username, @Param("iconUrl") String iconUrl);

    // ----- 角色权限 -----

    /** 按角色查询（后台 / 统计用） */
    List<User> findByRole(Role role);

    /** 按角色统计数量（占位后台用） */
    long countByRole(Role role);

    /**
     * 更新角色 + 过期时间。
     * VIP/SVIP 应传非空 expireAt；PLAYER/ADMIN 传 null 即可。
     */
    @Modifying
    @Query("UPDATE User p SET p.role = :role, p.vipExpireAt = :vipExpireAt WHERE p.username = :username")
    int updateRoleAndVipExpireByUsername(@Param("username") String username,
                                          @Param("role") Role role,
                                          @Param("vipExpireAt") LocalDateTime vipExpireAt);
}
