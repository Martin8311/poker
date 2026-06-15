package martin.game.websocket;

import martin.game.model.GameMessage;
import martin.game.model.Room;
import martin.game.model.User;
import martin.game.service.RoomService;
import martin.game.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 房间消息处理器：处理玩家加入/离开事件，广播房间状态更新
 */
@Controller
public class RoomMessageHandler {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    private static final Logger logger = LogManager.getLogger(RoomMessageHandler.class);

    /**
     * 玩家加入房间后，通知所有玩家更新列表
     * 客户端发送消息到 /app/rooms/{roomId}/join
     * 服务器广播到 /topic/rooms.{roomId}
     */
    @MessageMapping("/rooms/{roomId}/join")
    @SendTo("/topic/rooms.{roomId}")
    public GameMessage handlePlayerJoin(@DestinationVariable String roomId,
                                   GameMessage gameMessage,
                                   Authentication authentication) throws Exception {

        String username = authentication.getName();
        User currentUser = userService.findByUsername(username);

        if (currentUser == null) {
            throw new RuntimeException("用户未登录");
        }

        Room room = roomService.getRoom(roomId);

        if(!currentUser.getUsername().equals(room.getCreator().getUsername())) {
            logger.info("玩家" + currentUser.getUsername() + "加入了房间" + room.getRoomId());
        }else{
            logger.info("房主" + room.getCreator().getUsername() + "创建了房间" + room.getRoomId());
        }

        logger.info(room.getPlayers());
        logger.info(room.getSeatTypePlayerMap());

        gameMessage.setContent(currentUser.getNickname() + " 加入了房间!");
        gameMessage.setSenderNickname("System");
        gameMessage.setSeatPlayerMap(room.getSeatTypePlayerMap());
        gameMessage.setNumOfPlayers(room.getPlayers().size());
        gameMessage.setRelatedName(username);
        gameMessage.setCurrentSeat(room.getCurrentSeatByUsername(username));
        return gameMessage;
    }

    /**
     * 玩家离开房间后，通知所有玩家更新列表
     */
    @MessageMapping("/rooms/{roomId}/leave")
    @SendTo("/topic/rooms.{roomId}")
    public GameMessage handlePlayerLeave(@DestinationVariable String roomId,
                                         GameMessage gameMessage
                                         ) throws Exception {

        logger.error(gameMessage);

        User currentUser = userService.findByUsername(gameMessage.getRelatedName());
        if (currentUser == null) {
            throw new RuntimeException("用户未登录");
        }

        // 在分布式锁内移除玩家并写回，返回剩余人数
        Integer remaining = roomService.executeWithLock(roomId, room -> {
            room.removePlayer(currentUser);
            gameMessage.setSeatPlayerMap(room.getSeatTypePlayerMap());
            gameMessage.setNumOfPlayers(room.getPlayers().size());
            return room.getPlayers().size();
        });

        gameMessage.setContent(currentUser.getNickname() + " 离开了房间!");
        logger.info(currentUser.getNickname() + " 离开了房间!");
        gameMessage.setSenderNickname("System");

        // 房间空了则删除
        if (remaining != null && remaining == 0) {
            roomService.removeRoom(roomId);
        }

        return gameMessage;
    }

    @PostMapping("/rooms/{roomId}/giveUpRecover")
    @ResponseBody
    public GameMessage handlePlayerGiveUpRecover(@PathVariable String roomId,
                                         GameMessage gameMessage
                                         ) throws Exception {

        handlePlayerLeave(roomId, gameMessage);
        return gameMessage;
    }

    @MessageMapping("/rooms/{roomId}/getInfo")
    @SendTo("/topic/rooms.{roomId}")
    public GameMessage handlePlayerGetInfo(@DestinationVariable String roomId,
                                         GameMessage gameMessage,
                                         Authentication authentication) throws Exception {

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            throw new RuntimeException("用户未登录");
        }

        gameMessage.setRelatedName(currentUser.getUsername());
        return gameMessage;
    }
}