package martin.game.service;

import martin.game.model.Room;
import martin.game.model.User;
import martin.game.utils.SHA256Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class RoomService {
    // 使用ConcurrentHashMap确保线程安全的房间存储
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    // 为每个房间创建独立的锁，实现细粒度控制
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();

    private static final Logger logger = LogManager.getLogger(RoomService.class);

    /**
     * 创建新房间
     */
    public Room createRoom(User creator, String roomDesc, String roomPassword) {
        String roomId = generateUniqueRoomId();

        Room room = new Room(roomId, creator);
        room.setInfo(roomDesc);
        if(!roomPassword.equals("null")){
            roomPassword = SHA256Utils.sha256Encrypt(roomPassword);
            logger.info(creator.getUsername() + "创建了密码房间" + roomId + " 密码:" + roomPassword);
            room.setRoomPassword(roomPassword);
            room.setPublicRoom(false);
        }

        rooms.put(roomId, room);
        logger.info(creator.getUsername() + "创建了房间" + roomId);
        roomLocks.put(roomId, new ReentrantLock());
        return room;
    }

    /**
     * 获取房间
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 移除房间（当最后一个玩家离开时）
     */
    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }


    /**
     * 加入房间
     * 使用tryLock避免长时间阻塞
     */
    public boolean joinRoom(String roomId, User user) {
        ReentrantLock lock = roomLocks.get(roomId);
        if (lock == null) {
            return false;
        }

        try {
            // 尝试获取锁，5秒超时
            if (lock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)) {
                try {
                    Room room = rooms.get(roomId);
                    if (room != null && room.getPlayers().size() < 5) {
                        return room.addPlayer(user);
                    }
                    return false;
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * 处理玩家发出准备 / 取消准备事件
     */
    public void handleReadyEvent(String roomId, boolean isReady){
        getRoom(roomId).readyChange(isReady);
    }

    /**
     * 获取所有可加入的房间（人数不足5人）
     */
    public Set<Room> getAvailableRooms() {
        return rooms.values().stream()
                .filter(room -> room.getPlayers().size() < 5 && !room.isGameStarted())
                .collect(Collectors.toSet());
    }

    /**
     * 获取房间并加锁（用于游戏操作）
     */
    public Room getRoomWithLock(String roomId) {
        ReentrantLock lock = roomLocks.get(roomId);
        if (lock != null) {
            lock.lock();
            return rooms.get(roomId);
        }
        return null;
    }

    /**
     * 释放房间锁
     */
    public void releaseRoomLock(String roomId) {
        ReentrantLock lock = roomLocks.get(roomId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    // 生成唯一房间ID
    private String generateUniqueRoomId() {
        return "ROOM_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    public String check_reconnect(String username){
        for(Room room : rooms.values()){
            for(User u : room.getPlayers()){
                if(u.getUsername().equals(username)){
                    return room.getRoomId();
                }
            }
        }
        return "none";
    }
}
