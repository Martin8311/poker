package martin.game.service;

import martin.game.model.Room;
import martin.game.model.User;
import martin.game.repository.RoomRepository;
import martin.game.utils.SHA256Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.function.Function;

/**
 * 房间服务。房间状态外置到 Redis（多实例共享），并发用 Redisson 分布式锁。
 *
 * <p>所有"读-改-写回"统一走 {@link #executeWithLock}：加分布式锁 → 从 Redis 读 Room
 * → 业务回调修改 → 写回 Redis → 解锁，保证多实例下对同一房间的操作串行、一致。
 */
@Service
public class RoomService {

    private static final String LOCK_PREFIX = "poker:room:lock:";

    private final RoomRepository roomRepository;
    private final RedissonClient redissonClient;

    private static final Logger logger = LogManager.getLogger(RoomService.class);

    public RoomService(RoomRepository roomRepository, RedissonClient redissonClient) {
        this.roomRepository = roomRepository;
        this.redissonClient = redissonClient;
    }

    /**
     * 在分布式锁保护下读取 Room、执行业务修改并写回 Redis。
     *
     * @return 业务回调的返回值；房间不存在时返回 null
     */
    public <T> T executeWithLock(String roomId, Function<Room, T> action) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + roomId);
        lock.lock();
        try {
            Room room = roomRepository.findById(roomId);
            if (room == null) {
                return null;
            }
            T result = action.apply(room);
            roomRepository.save(room);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建新房间
     */
    public Room createRoom(User creator, String roomDesc, String roomPassword) {
        String roomId = generateUniqueRoomId();

        Room room = new Room(roomId, creator);
        room.setInfo(roomDesc);
        if (!roomPassword.equals("null")) {
            roomPassword = SHA256Utils.sha256Encrypt(roomPassword);
            room.setRoomPassword(roomPassword);
            room.setPublicRoom(false);
            logger.info(creator.getUsername() + "创建了密码房间" + roomId);
        }

        roomRepository.save(room);
        logger.info(creator.getUsername() + "创建了房间" + roomId);
        return room;
    }

    /**
     * 获取房间（只读，无锁）
     */
    public Room getRoom(String roomId) {
        return roomRepository.findById(roomId);
    }

    /**
     * 校验用户是否为指定房间的成员（只读，用于 WebSocket 订阅鉴权）
     */
    public boolean isUserInRoom(String roomId, String username) {
        Room room = roomRepository.findById(roomId);
        if (room == null) {
            return false;
        }
        for (User u : room.getPlayers()) {
            if (u.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 移除房间（当最后一个玩家离开时）
     */
    public void removeRoom(String roomId) {
        roomRepository.delete(roomId);
    }

    /**
     * 加入房间（分布式锁保护）
     */
    public boolean joinRoom(String roomId, User user) {
        Boolean result = executeWithLock(roomId, room -> {
            if (room.getPlayers().size() < 5) {
                return room.addPlayer(user);
            }
            return false;
        });
        return Boolean.TRUE.equals(result);
    }

    /**
     * 处理玩家发出准备 / 取消准备事件
     */
    public void handleReadyEvent(String roomId, boolean isReady) {
        executeWithLock(roomId, room -> {
            room.readyChange(isReady);
            return null;
        });
    }

    /**
     * 获取所有可加入的房间（人数不足 5 人且未开始）
     */
    public Set<Room> getAvailableRooms() {
        return roomRepository.findAllAvailable();
    }

    // 生成唯一房间 ID
    private String generateUniqueRoomId() {
        return "ROOM_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    /**
     * 重连检测：扫描所有房间，找到该用户所在房间
     */
    public String check_reconnect(String username) {
        for (String roomId : roomRepository.allRoomIds()) {
            Room room = roomRepository.findById(roomId);
            if (room == null) {
                continue;
            }
            for (User u : room.getPlayers()) {
                if (u.getUsername().equals(username)) {
                    return roomId;
                }
            }
        }
        return "none";
    }
}
