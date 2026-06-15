package martin.game.repository;

import jakarta.persistence.LockModeType;
import martin.game.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
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

}
