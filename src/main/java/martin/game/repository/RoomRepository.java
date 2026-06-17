package martin.game.repository;

import martin.game.model.Room;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * 房间的 Redis 存储层。
 *
 * <pre>
 * poker:room:{roomId}  -> Room 的 JSON（含房间元数据 + 对局状态）
 * poker:rooms          -> Set，所有房间 ID 的索引（供大厅列表 / 重连扫描）
 * </pre>
 *
 * 并发安全由 {@code RoomService.executeWithLock} 的分布式锁在外层保证，本层只做读写。
 */
@Repository
public class RoomRepository {

    private static final String ROOM_KEY_PREFIX = "poker:room:";
    private static final String ROOM_INDEX = "poker:rooms";

    private final RedisTemplate<String, Object> redisTemplate;

    /** 房间 key 的 TTL（秒）。每次 save 设置、定时续租刷新；无人续租则随 TTL 过期回收 */
    @Value("${app.room.ttl-seconds:90}")
    private long ttlSeconds;

    public RoomRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Room room) {
        redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + room.getRoomId(), room, Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForSet().add(ROOM_INDEX, room.getRoomId());
    }

    /** 续租：刷新房间 key 的 TTL（由 RoomLivenessTracker 对仍有在线会话的房间定时调用） */
    public void touch(String roomId) {
        redisTemplate.expire(ROOM_KEY_PREFIX + roomId, Duration.ofSeconds(ttlSeconds));
    }

    public Room findById(String roomId) {
        Object value = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId);
        return value instanceof Room ? (Room) value : null;
    }

    public void delete(String roomId) {
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId);
        redisTemplate.opsForSet().remove(ROOM_INDEX, roomId);
    }

    public boolean exists(String roomId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ROOM_KEY_PREFIX + roomId));
    }

    /** 所有房间 ID（供重连扫描等）。顺带自愈：清理 key 已被 TTL 回收、但索引仍残留的 id */
    public Set<String> allRoomIds() {
        Set<Object> ids = redisTemplate.opsForSet().members(ROOM_INDEX);
        Set<String> result = new HashSet<>();
        if (ids != null) {
            for (Object id : ids) {
                String roomId = String.valueOf(id);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(ROOM_KEY_PREFIX + roomId))) {
                    result.add(roomId);
                } else {
                    redisTemplate.opsForSet().remove(ROOM_INDEX, roomId);
                }
            }
        }
        return result;
    }

    /** 可加入的房间（人数 < 5 且未开始） */
    public Set<Room> findAllAvailable() {
        Set<Room> result = new HashSet<>();
        for (String roomId : allRoomIds()) {
            Room room = findById(roomId);
            if (room != null && room.getPlayers().size() < 5 && !room.isGameStarted()) {
                result.add(room);
            }
        }
        return result;
    }
}
