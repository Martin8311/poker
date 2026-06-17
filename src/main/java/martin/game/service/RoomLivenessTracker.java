package martin.game.service;

import martin.game.repository.RoomRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间在线会话追踪 + 租约续租，用于回收「房主非正常退出后残留的空房间」。
 *
 * <p>房间在 Redis 中带 TTL（租约）。本组件维护「WebSocket 会话 → 房间」映射，定时给
 * 「仍有在线会话」的房间续租；当一个房间在所有实例上都没有在线会话时（关页面 / 断网 /
 * 实例崩溃），没人续租 → Redis 自动过期回收。
 *
 * <p>每个实例只追踪连到自己的会话，各自续租自己有连接的房间，多实例下天然协同；
 * 实例崩溃时它的续租随之停止，所持房间自动过期，无需额外清理。
 */
@Component
public class RoomLivenessTracker {

    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms.";

    /** sessionId -> roomId（仅本实例的在线会话） */
    private final ConcurrentHashMap<String, String> sessionToRoom = new ConcurrentHashMap<>();

    private final RoomRepository roomRepository;
    private static final Logger logger = LogManager.getLogger(RoomLivenessTracker.class);

    public RoomLivenessTracker(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /** 客户端订阅房间话题 → 记录该会话所在房间 */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith(ROOM_TOPIC_PREFIX)) {
            String roomId = destination.substring(ROOM_TOPIC_PREFIX.length());
            sessionToRoom.put(accessor.getSessionId(), roomId);
        }
    }

    /** 会话断开（关页面 / 断网 / 正常断开）→ 移除该会话 */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessionToRoom.remove(event.getSessionId());
    }

    /**
     * 启动时给已存在的房间设上租约：本次改动之前创建的房间在 Redis 中没有 TTL，
     * 这里统一补上，随后无人续租的（含历史残留的孤儿房间）将随 TTL 过期回收。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void leaseExistingRoomsOnStartup() {
        Set<String> rooms = roomRepository.allRoomIds();
        for (String roomId : rooms) {
            roomRepository.touch(roomId);
        }
        logger.info("启动续租：为 {} 个已存在房间设置 TTL，无人续租者将被回收", rooms.size());
    }

    /** 定时给仍有在线会话的房间续租；无人续租的房间会随 TTL 过期被回收 */
    @Scheduled(fixedRateString = "${app.room.touch-interval-ms:30000}")
    public void renewLeases() {
        Set<String> liveRooms = new HashSet<>(sessionToRoom.values());
        for (String roomId : liveRooms) {
            roomRepository.touch(roomId);
        }
    }
}
