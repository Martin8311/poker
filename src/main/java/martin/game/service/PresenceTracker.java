package martin.game.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线状态追踪（"是否在线" = 当前是否有活跃的 STOMP 连接）。
 *
 * <p>沿用 {@link RoomLivenessTracker} 的心跳续租思路：在线用户写进 Redis ZSet
 * {@code presence:online}（member=username, score=最近心跳时间戳）。
 * <ul>
 *   <li>连接事件 → 立即标记在线；</li>
 *   <li>每个实例定时给"连在自己身上"的用户刷新 score（心跳）；</li>
 *   <li>定时清理 score 过期的成员 —— 含浏览器崩溃 / 实例宕机遗留的"幽灵在线"。</li>
 * </ul>
 * 在线判定 = score >= now - ttl。多实例共享同一 ZSet，天然跨实例、崩溃自愈、无需协调。
 */
@Component
public class PresenceTracker {

    private static final String PRESENCE_KEY = "presence:online";

    /** sessionId -> username（仅本实例的在线会话） */
    private final ConcurrentHashMap<String, String> sessionToUser = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    /** 多久没有心跳即视为离线（毫秒）。应 > 心跳间隔，给一次丢失留余量 */
    private final long ttlMs;

    private static final Logger logger = LogManager.getLogger(PresenceTracker.class);

    public PresenceTracker(StringRedisTemplate redis,
                           @Value("${app.presence.ttl-ms:60000}") long ttlMs) {
        this.redis = redis;
        this.ttlMs = ttlMs;
    }

    /** STOMP 连接建立 → 记录会话并立即标记在线 */
    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user != null && sessionId != null) {
            sessionToUser.put(sessionId, user.getName());
            touch(user.getName());
        }
    }

    /** 会话断开 → 移除映射（不立即删 ZSet，靠心跳停止后过期判离线，避免多标签误判） */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessionToUser.remove(event.getSessionId());
    }

    /** 心跳：给本实例当前所有在线用户刷新 score */
    @Scheduled(fixedRateString = "${app.presence.heartbeat-ms:20000}")
    public void heartbeat() {
        Set<String> online = new HashSet<>(sessionToUser.values());
        long now = System.currentTimeMillis();
        for (String username : online) {
            redis.opsForZSet().add(PRESENCE_KEY, username, now);
        }
    }

    /** 清理：移除 score 过期的成员（崩溃 / 异常退出遗留的在线记录） */
    @Scheduled(fixedRateString = "${app.presence.cleanup-ms:30000}")
    public void cleanup() {
        long stale = System.currentTimeMillis() - ttlMs;
        try {
            redis.opsForZSet().removeRangeByScore(PRESENCE_KEY, 0, stale);
        } catch (Exception e) {
            logger.warn("清理在线状态失败: {}", e.getMessage());
        }
    }

    private void touch(String username) {
        redis.opsForZSet().add(PRESENCE_KEY, username, System.currentTimeMillis());
    }

    /** 单个用户是否在线 */
    public boolean isOnline(String username) {
        Double score = redis.opsForZSet().score(PRESENCE_KEY, username);
        return score != null && score >= System.currentTimeMillis() - ttlMs;
    }

    /** 批量：给定用户名集合中，当前在线的子集（一次 Redis 调用） */
    public Set<String> onlineAmong(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptySet();
        }
        long threshold = System.currentTimeMillis() - ttlMs;
        Set<String> onlineNow = redis.opsForZSet().rangeByScore(PRESENCE_KEY, threshold, Double.MAX_VALUE);
        if (onlineNow == null || onlineNow.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>(usernames);
        result.retainAll(onlineNow);
        return result;
    }
}
