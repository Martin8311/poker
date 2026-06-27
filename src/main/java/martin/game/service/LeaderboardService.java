package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.dto.LeaderboardEntry;
import martin.game.model.User;
import martin.game.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 排行榜服务：基于 Redis ZSet 实现积分榜。
 *
 * <pre>
 * Key:    leaderboard:score
 * member: username
 * score:  累计积分（与 MySQL user.score 保持最终一致）
 * </pre>
 *
 * 设计要点：
 * <ol>
 *     <li>结算时用 {@code ZINCRBY} 累加积分，O(log N)，无需每次对全表排序；</li>
 *     <li>TopN 用 {@code ZREVRANGE}、个人排名用 {@code ZREVRANK}，均为对数级复杂度；</li>
 *     <li>Redis 仅作"读加速"，写入失败时降级（不阻断积分入库主流程）；</li>
 *     <li>应用启动时从 DB 预热重建，保证缓存与持久层一致。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService implements ApplicationRunner {

    public static final String LEADERBOARD_KEY = "leaderboard:score";

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;

    private static final Logger logger = LogManager.getLogger(LeaderboardService.class);

    /**
     * 累加用户积分（结算时调用）。
     * Redis 异常时降级处理，不影响积分入库主流程；启动预热与定期重建可纠正偏差。
     */
    public void addScore(String username, long delta) {
        try {
            redis.opsForZSet().incrementScore(LEADERBOARD_KEY, username, delta);
        } catch (Exception e) {
            logger.warn("更新排行榜失败（已降级）: user={}, delta={}, err={}", username, delta, e.getMessage());
        }
    }

    /**
     * 取积分前 N 名。
     */
    public List<LeaderboardEntry> getTopN(int n) {
        if (n <= 0) {
            n = 10;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, n - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量补全昵称 / 头像，避免逐条查库（N+1）
        List<String> usernames = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .collect(Collectors.toList());
        Map<String, User> userMap = userRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(User::getUsername, u -> u, (a, b) -> a));

        List<LeaderboardEntry> result = new ArrayList<>(tuples.size());
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            String username = t.getValue();
            double score = t.getScore() == null ? 0 : t.getScore();
            User u = userMap.get(username);
            String role = u != null ? u.getEffectiveRole().name() : martin.game.model.Role.PLAYER.name();
            result.add(new LeaderboardEntry(
                    rank++,
                    username,
                    u != null ? u.getNickname() : username,
                    avatarUrl(u),
                    (long) score,
                    role
            ));
        }
        return result;
    }

    /**
     * 查询某用户的排名与积分（未上榜时 rank 返回 -1）。
     */
    public LeaderboardEntry getUserEntry(String username) {
        Long rank = redis.opsForZSet().reverseRank(LEADERBOARD_KEY, username);
        Double score = redis.opsForZSet().score(LEADERBOARD_KEY, username);

        User u = userRepository.findByUsername(username).orElse(null);
        String role = u != null ? u.getEffectiveRole().name() : martin.game.model.Role.PLAYER.name();
        return new LeaderboardEntry(
                rank == null ? -1 : rank + 1,
                username,
                u != null ? u.getNickname() : username,
                avatarUrl(u),
                score == null ? 0L : score.longValue(),
                role
        );
    }

    /**
     * 从数据库全量重建排行榜 ZSet（启动预热 / 数据修复）。
     *
     * @return 载入的用户数
     */
    public long rebuildFromDb() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            return 0;
        }

        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (User u : users) {
            int score = u.getScore() == null ? 0 : u.getScore();
            tuples.add(new DefaultTypedTuple<>(u.getUsername(), (double) score));
        }

        // 先删旧键再批量写入，保证与 DB 完全一致
        redis.delete(LEADERBOARD_KEY);
        redis.opsForZSet().add(LEADERBOARD_KEY, tuples);
        return users.size();
    }

    /**
     * 应用启动后预热排行榜。
     * Redis 不可用时静默跳过，不影响应用启动；后续可通过结算 / 手动重建恢复。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            long count = rebuildFromDb();
            logger.info("排行榜预热完成，加载 {} 名用户", count);
        } catch (Exception e) {
            logger.warn("排行榜预热失败（Redis 可能未就绪）: {}", e.getMessage());
        }
    }

    private String avatarUrl(User user) {
        if (user == null || user.getIconUrl() == null || user.getIconUrl().isBlank()) {
            return "/icon/default-avatar.jpg";
        }
        String iconUrl = user.getIconUrl().trim();
        if (iconUrl.startsWith("http://") || iconUrl.startsWith("https://")
                || iconUrl.startsWith("/") || iconUrl.startsWith("data:")) {
            return iconUrl;
        }
        return "/avatar/" + iconUrl;
    }
}
