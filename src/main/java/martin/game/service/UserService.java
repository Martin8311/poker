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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final DateTimeFormatter BAN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    // private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println(username);
        // 1. 根据用户名查询数据库中的用户实体
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));

        // 2. 返回自定义的 LoginUser（包含用户 ID）
        clearExpiredBanIfNeeded(user, LocalDateTime.now());
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

    @Transactional
    public boolean updatePassword(String username, String rawPassword) {
        String password = rawPassword == null ? "" : rawPassword.trim();
        if (password.length() < 6 || password.length() > 64) {
            throw new IllegalArgumentException("密码长度需要在 6 到 64 位之间");
        }
        int affectedRows = userRepository.updatePasswordByUsername(username, passwordEncoder.encode(password));
        return affectedRows == 1;
    }

    @Transactional
    public boolean banUser(String operatorUsername, String username, String reason) {
        String operator = normalizeUsername(operatorUsername, "管理员用户名不能为空");
        String target = normalizeUsername(username, "用户名不能为空");
        if (operator.equals(target)) {
            throw new IllegalArgumentException("不能封禁自己");
        }

        User targetUser = findByUsername(target);
        if (targetUser.getEffectiveRole() == Role.ADMIN) {
            throw new IllegalArgumentException("不能封禁管理员账号");
        }

        int affectedRows = userRepository.banByUsername(target, normalizeBanReason(reason), LocalDateTime.now(), null);
        return affectedRows == 1;
    }

    @Transactional
    public boolean banUser(String operatorUsername, String username, String reason,
                           Integer durationAmount, String durationUnit) {
        String operator = normalizeUsername(operatorUsername, "管理员用户名不能为空");
        String target = normalizeUsername(username, "用户名不能为空");
        if (operator.equals(target)) {
            throw new IllegalArgumentException("不能封禁自己");
        }

        User targetUser = findByUsername(target);
        if (targetUser.getEffectiveRole() == Role.ADMIN) {
            throw new IllegalArgumentException("不能封禁管理员账号");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime banExpireAt = calculateBanExpireAt(durationAmount, durationUnit, now);
        int affectedRows = userRepository.banByUsername(target, normalizeBanReason(reason), now, banExpireAt);
        return affectedRows == 1;
    }

    @Transactional
    public boolean unbanUser(String username) {
        String target = normalizeUsername(username, "用户名不能为空");
        findByUsername(target);
        int affectedRows = userRepository.unbanByUsername(target);
        return affectedRows == 1;
    }

    public boolean isBanned(String username) {
        User user = findByUsername(username);
        return !clearExpiredBanIfNeeded(user, LocalDateTime.now()) && user.isBanActive();
    }

    public String getActiveBanMessage(String username) {
        User user = findByUsername(username);
        if (clearExpiredBanIfNeeded(user, LocalDateTime.now()) || !user.isBanActive()) {
            return null;
        }
        String reason = user.getBanReason() == null || user.getBanReason().isBlank()
                ? "管理员封禁"
                : user.getBanReason();
        String duration = user.getBanExpireAt() == null
                ? "永久"
                : "至 " + user.getBanExpireAt().format(BAN_TIME_FORMATTER);
        return "账号已被封禁。原因：" + reason + "。封禁时长：" + duration;
    }

    public List<User> findAdminUsers(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            List<User> allUsers = userRepository.findByRole(Role.PLAYER);
            allUsers.addAll(userRepository.findByRole(Role.VIP));
            allUsers.addAll(userRepository.findByRole(Role.SVIP));
            allUsers.addAll(userRepository.findByRole(Role.ADMIN));
            return allUsers;
        }
        return userRepository.searchByUsernameOrNickname(normalized);
    }

    private String normalizeUsername(String username, String message) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeBanReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isBlank()) {
            normalized = "管理员封禁";
        }
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private LocalDateTime calculateBanExpireAt(Integer amount, String unit, LocalDateTime now) {
        String normalizedUnit = unit == null ? "PERMANENT" : unit.trim().toUpperCase();
        if (normalizedUnit.isBlank() || "PERMANENT".equals(normalizedUnit)) {
            return null;
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("封禁时长必须大于 0");
        }
        return switch (normalizedUnit) {
            case "HOURS" -> now.plusHours(amount);
            case "DAYS" -> now.plusDays(amount);
            default -> throw new IllegalArgumentException("不支持的封禁时长单位");
        };
    }

    private boolean clearExpiredBanIfNeeded(User user, LocalDateTime now) {
        if (Boolean.TRUE.equals(user.getBanned())
                && user.getBanExpireAt() != null
                && !user.getBanExpireAt().isAfter(now)) {
            userRepository.unbanByUsername(user.getUsername());
            user.setBanned(false);
            user.setBanReason(null);
            user.setBannedAt(null);
            user.setBanExpireAt(null);
            return true;
        }
        return false;
    }

    public boolean isPhoneBoundToOtherUser(String username, String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(user -> !user.getUsername().equals(username))
                .orElse(false);
    }

    public User findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("该手机号未绑定账号"));
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
