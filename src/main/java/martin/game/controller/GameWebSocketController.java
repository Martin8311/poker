package martin.game.controller;

import martin.game.model.*;
import martin.game.service.GameService;
import martin.game.service.RoomService;
import martin.game.service.UserService;
import martin.game.utils.GameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class GameWebSocketController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Autowired
    private GameService gameService;

    private static final Logger logger = LogManager.getLogger(GameWebSocketController.class);

    /**
     * 处理房间内的聊天消息
     */
    @MessageMapping("/rooms/{roomId}/message")
    @SendTo("/topic/rooms.{roomId}")
    public GameMessage handleRoomMessage(
            @DestinationVariable String roomId,
            GameMessage message,
            Authentication authentication) {

        String username = authentication.getName();
        User user = userService.findByUsername(username);

        message.setSenderNickname(user.getNickname());
        message.setTimestamp(System.currentTimeMillis());
        logger.info("用户" + user.getUsername() + "发送信息:" + message.getContent());
        return message;
    }

    /**
     * 处理游戏开始(由房主开始)
     */
    @MessageMapping("/rooms/{roomId}/start")
    @SendTo("/topic/rooms.{roomId}")
    public GameAction handleGameStart(
            @DestinationVariable String roomId,
            GameAction action,
            Authentication authentication) {

        roomService.executeWithLock(roomId, room -> {
            if (room.getPlayers().size() < 5) {
                action.setMessage("人数不足 5 人, 无法开始");
                action.setStatus("error");
            } else if (room.getReadyNumOfPlayers() != 4) {
                action.setMessage("有玩家未准备, 无法开始");
                action.setStatus("error");
            } else {
                action.setMessage("游戏即将开始");
                action.setStatus("success");
                room.setGameState(GameState.READY_TO_START);
                room.setGameStarted(true);
            }
            return null;
        });

        return action;
    }

    /**
     * 处理发牌逻辑
     */
    @MessageMapping("/rooms/{roomId}/allocate")
    @SendTo("/topic/rooms.{roomId}")
    public GameAction handleGameAllocate(
            @DestinationVariable String roomId,
            GameAction action,
            Authentication authentication) {

        Room room = roomService.getRoom(roomId);
        if (!action.getUsername().equals(room.getCreator().getUsername())) {
            action.setType("UN_KNOWN");
            return action;
        }

        gameService.startGame(roomId); // 内部发牌、排序并写回 Redis

        // 广播发牌消息，每个玩家再通过 POST 取自己的牌，避免被他人看见
        action.setType("ALLOCATE_CARD");
        return action;
    }

    @PostMapping("/rooms/{roomId}/get_card")
    public ResponseEntity<List<Card>> handleGameGetCard(@PathVariable String roomId, @RequestBody String username) {
        Room room = roomService.getRoom(roomId);
        List<Card> cards = room.getPlayersCards().get(username);
        return ResponseEntity.ok(cards);
    }

    /**
     *  指定轮次
     */
    @MessageMapping("/rooms/{roomId}/turn")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameTurn(@DestinationVariable String roomId,
                                    GameRound round,
                                    Authentication authentication) {

        roomService.executeWithLock(roomId, room -> {
            String firstPlayer = gameService.initFirstTurn(room);
            room.setLastActorUsername(null);
            room.setLastCards(null);

            round.setCurrentTurnPlayer(firstPlayer);
            round.setPlayerCards(null);
            round.setType("Turn");
            round.setFirst(true);
            return null;
        });
        return round;
    }

    /**
     *  处理出牌
     */
    @MessageMapping("/rooms/{roomId}/process-actor")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameActor(@DestinationVariable String roomId,
                                     GameRound round,
                                     Authentication authentication) {

        roomService.executeWithLock(roomId, room -> {
            room.setLastCards(round.getPlayerCards());
            room.setLastActorUsername(round.getCurrentTurnPlayer());

            String nextActorUsername = gameService.nextActor(room, round.getCurrentTurnPlayer());
            round.setNextActor(nextActorUsername);
            room.setCurrentActorUsername(nextActorUsername);
            round.setType("ACTOR");

            round.setFirst(false); // 下一人不带牌权
            room.setFirst(false);
            return null;
        });
        return round;
    }

    @PostMapping("/rooms/{roomId}/actor")
    public ResponseEntity<String> handleGameActor(@PathVariable String roomId, @RequestBody GameRound round) {
        List<Card> cards = round.getPlayerCards();

        String result = roomService.executeWithLock(roomId, room -> {
            if (GameUtils.check(cards, room.getLastCards())) {
                GameUtils.removeCards(room.getPlayersCards().get(round.getCurrentTurnPlayer()), cards);
                return "ok";
            }
            return "你选择的牌不符合规则!";
        });

        return ResponseEntity.ok(result != null ? result : "房间不存在");
    }

    /**
     * PASS
     */
    @MessageMapping("/rooms/{roomId}/pass")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGamePass(@DestinationVariable String roomId, GameRound round) {
        roomService.executeWithLock(roomId, room -> {
            round.setType("PASS");

            String nextActorUsername = gameService.nextActor(room, round.getCurrentTurnPlayer());
            round.setNextActor(nextActorUsername);
            room.setCurrentActorUsername(nextActorUsername);
            round.setPlayerCards(null);

            // 下一出牌人 与 上一出牌人为同一人时 形成牌权
            if (round.getNextActor().equals(room.getLastActorUsername())) {
                round.setFirst(true);
                room.setLastCards(null);
                room.setFirst(true);
            } else {
                round.setFirst(false);
                room.setFirst(false);
            }
            return null;
        });
        return round;
    }

    /**
     * 接受玩家手牌出空请求
     */
    @MessageMapping("/rooms/{roomId}/hand-empty")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameHandEmptyRequest(@DestinationVariable String roomId, GameRound round) {
        roomService.executeWithLock(roomId, room -> {
            String username = round.getCurrentTurnPlayer();

            room.getHandEmptyMap().put(username, true);
            Integer size = room.getPlayersSettlementSequenceMap().size() + 1;
            room.getPlayersSettlementSequenceMap().put(username, size);
            room.getPlayersSettlementSequencesMap().put(size, username);

            // 有 4 名玩家出空 或 提前判定结束 → 游戏结束并结算
            if (room.getPlayersSettlementSequenceMap().size() >= 4 || gameService.checkGameIsOver(room)) {
                Set<User> s = room.getPlayers();
                Map<String, Integer> nicknameWithScoreMap = new HashMap<>();
                Map<String, Integer> totalScoreMap = room.getTotalScoreMap();
                Map<String, Integer> nicknameWithTotalScoreMap = new HashMap<>();

                // 给尚未出空的玩家补上结算顺序
                for (User user : s) {
                    if (!room.getPlayersSettlementSequenceMap().containsKey(user.getUsername())) {
                        Integer cap = room.getPlayersSettlementSequenceMap().size() + 1;
                        room.getPlayersSettlementSequenceMap().put(user.getUsername(), cap);
                        room.getPlayersSettlementSequencesMap().put(cap, user.getUsername());
                    }
                }

                round.setType("GAME_OVER");
                Map<String, Integer> scoreMap = gameService.calculateResult(room);

                for (User user : s) {
                    String name = user.getUsername();
                    userService.updateUserScoreInfo(name, scoreMap.get(name));
                    totalScoreMap.put(name, totalScoreMap.get(name) + scoreMap.get(name));
                    nicknameWithScoreMap.put(user.getNickname(), scoreMap.get(name));
                    nicknameWithTotalScoreMap.put(user.getNickname(), totalScoreMap.get(name));
                }

                round.setScoreMap(nicknameWithScoreMap);
                round.setTotalScoreMap(nicknameWithTotalScoreMap);
                logger.info(scoreMap);
                gameService.restartGame(room); // 重置并重新发牌（同一锁内写回）
                return null;
            }

            room.setLastActorUsername(gameService.nextActor(room, username)); // 给风

            round.setType("HAND_EMPTY");
            round.setActor(username);
            round.setTeam(room.getPlayersTeamMap().get(username));
            round.setOrder(room.getPlayersSettlementSequenceMap().get(username));
            return null;
        });
        return round;
    }

    /**
     * restart 重新对局（手牌已在 restartGame 中重新发好，这里只广播通知）
     */
    @MessageMapping("/rooms/{roomId}/restart")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameRestart(@DestinationVariable String roomId, @RequestBody GameRound round) {
        round.setType("GAME_RESTART");
        return round;
    }

    /**
     * recover 重连恢复（只读）
     */
    @PostMapping("/rooms/{roomId}/recover")
    @ResponseBody
    public GameRound handleGameRecover(@PathVariable String roomId, @RequestBody String username) {
        Room room = roomService.getRoom(roomId);
        GameRound round = new GameRound();
        round.setType("RECOVER");
        round.setPlayerCards(room.getPlayersCards().get(username));
        round.setCurrentTurnPlayer(room.getCurrentActorUsername());
        round.setLastPlayerCards(room.getLastCards());
        round.setLastActor(room.getLastActorUsername());
        round.setFirst(room.isFirst());
        round.setBigGhostPlayerUsername(room.getBigGhostPlayerUsername());
        round.setPlayersSettlementSequenceMap(room.getPlayersSettlementSequenceMap());
        round.setPlayersTeamMap(room.getPlayersTeamMap());
        return round;
    }

}
