package martin.game.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import martin.game.model.Role;
import martin.game.model.User;
import martin.game.repository.UserRepository;
import martin.game.utils.LoginUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    // private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println(username);
        // 1. 根据用户名查询数据库中的用户实体
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));

        // 2. 返回自定义的 LoginUser（包含用户 ID）
        return new LoginUser(user);
    }

    public User findByUsername(String username){
        return userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    // 用户注册
    public void register(User user){
        if(userRepository.existsByUsername(user.getUsername())){
            throw new IllegalArgumentException("用户名已存在");
        }

//        if(userRepository.existsByEmail(user.getEmail())){
//            throw new IllegalArgumentException("邮箱已存在");
//        }

        if(userRepository.existsByNickname(user.getNickname())){
            throw new IllegalArgumentException("昵称已存在");
        }


        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepository.save(user);
    }

    /**
     * 更新用户游戏统计（对局数、胜局数、积分）
     * @param username 用户名
     * @param scoreChange 积分变化（正数增加，负数减少）
     */
    @Transactional
    public void updateUserScoreInfo(String username, int scoreChange){
        userRepository.updateScoreByUsername(username, scoreChange > 0 ? 1 : 0, scoreChange);
        // 同步更新 Redis 排行榜（ZINCRBY）；失败时已在内部降级，不影响积分入库
        leaderboardService.addScore(username, scoreChange);
    }

    /**
     * 更新玩家个人资料 头像
     * @return
     */
    @Transactional
    public boolean updataNickName (String username, String nickname) throws Exception{
        int affectedRows = userRepository.updateNickNameByUsername(username, nickname);
        return affectedRows == 1;
    }

    @Transactional
    public boolean updataIcon(String username, String iconUrl) throws Exception{
        int affectedRows = userRepository.updateIconUrlByUsername(username, iconUrl);
        return affectedRows == 1;
    }

    @Transactional
    public boolean updatePhone(String username, String phoneNumber) {
        int affectedRows = userRepository.updatePhoneByUsername(username, phoneNumber, LocalDateTime.now());
        return affectedRows == 1;
    }

    public boolean isPhoneBoundToOtherUser(String username, String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(user -> !user.getUsername().equals(username))
                .orElse(false);
    }

    // ============ 角色权限管理 ============

    /**
     * 设置用户角色 + 过期时间。
     * <ul>
     *     <li>PLAYER / ADMIN：expireAt 传 null；</li>
     *     <li>VIP / SVIP：expireAt 必须非空且在未来；</li>
     *     <li>VIP / SVIP 过期后无需手动降级：{@link User#getEffectiveRole()} 会在读路径自动回落 PLAYER。</li>
     * </ul>
     *
     * @return 是否成功更新（1 行受影响）
     */
    @Transactional
    public boolean setRole(String username, Role newRole, LocalDateTime vipExpireAt) {
        if (newRole == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        if ((newRole == Role.VIP || newRole == Role.SVIP)
                && (vipExpireAt == null || !vipExpireAt.isAfter(LocalDateTime.now()))) {
            throw new IllegalArgumentException("VIP/SVIP 必须设置未来的过期时间");
        }
        // PLAYER/ADMIN 时清空过期时间
        LocalDateTime effectiveExpire = (newRole == Role.VIP || newRole == Role.SVIP) ? vipExpireAt : null;
        int affected = userRepository.updateRoleAndVipExpireByUsername(username, newRole, effectiveExpire);
        return affected == 1;
    }

    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }
}
