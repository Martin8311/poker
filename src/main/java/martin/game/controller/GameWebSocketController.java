package martin.game.controller;

import jakarta.persistence.criteria.CriteriaBuilder;
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
import org.springframework.messaging.simp.user.SimpUserRegistry;
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

        Room room = roomService.getRoom(roomId);

        if(room.getPlayers().size() < 5){
            action.setMessage("人数不足 5 人, 无法开始");
            action.setStatus("error");
            /*
                TODO: Release版 请将条件置为: room.getReadyNumOfPlayers() != 4
                Debug版 room.getReadyNumOfPlayers() > 4
             */
        } else if(room.getReadyNumOfPlayers() != 4){
            action.setMessage("有玩家未准备, 无法开始");
            action.setStatus("error");
        } else{
            action.setMessage("游戏即将开始");
            action.setStatus("success");
            room.setGameState(GameState.READY_TO_START);
            room.setGameStarted(true);
        }

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

        if(!action.getUsername().equals(roomService.getRoom(roomId).getCreator().getUsername())){
            action.setType("UN_KNOWN");
            return action;
        }

        gameService.startGame(roomId);
        Map<String, List<Card>> playerCards = gameService.getRoomCards(roomId);
        GameUtils.sortPlayerCards(playerCards);

        // 广播发牌消息, 每个玩家再通过POST方法取牌，避免牌被他人看见
        action.setType("ALLOCATE_CARD");
        return action;
    }

    @PostMapping("/rooms/{roomId}/get_card")
    public ResponseEntity<List<Card>> handleGameGetCard(@PathVariable String roomId, @RequestBody String username){
        Map<String, List<Card>> playerCards = gameService.getRoomCards(roomId);

        logger.info(playerCards);
        List<Card> cards = playerCards.get(username);
        return ResponseEntity.ok(cards);
    }

    /**
     * 处理游戏动作的核心逻辑 TODO: 弃用
     */
    private void processGameAction(String roomId, GameAction action) {
        // 获取房间并加锁
        var room = roomService.getRoomWithLock(roomId);
        if (room == null) {
            action.setStatus("error");
            action.setMessage("房间不存在");
            return;
        }

        try {
            // 处理具体的游戏动作（出牌、跳过等）
            // 根据房间当前状态和游戏规则处理动作
            // ...

            action.setStatus("success");
        } finally {
            // 确保释放锁
            roomService.releaseRoomLock(roomId);
        }
    }

    /**
     *  指定轮次
     */
    @MessageMapping("/rooms/{roomId}/turn")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameTurn(@DestinationVariable String roomId,
                                    GameRound round,
                                    Authentication authentication){

        String firstPlayer = gameService.initFirstTurn(roomId);
        Room room = roomService.getRoom(roomId);

        room.setLastActorUsername(null);
        room.setLastCards(null);

        round.setCurrentTurnPlayer(firstPlayer);
        round.setPlayerCards(null);
        round.setType("Turn");
        round.setFirst(true);
        return round;
    }

    /**
     *  处理出牌
     */
    @MessageMapping("/rooms/{roomId}/process-actor")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameActor(@DestinationVariable String roomId,
                                     GameRound round,
                                     Authentication authentication){

        Room room = roomService.getRoom(roomId);

        room.setLastCards(round.getPlayerCards());
        room.setLastActorUsername(round.getCurrentTurnPlayer());

        String nextActorUsername = gameService.nextActor(roomId, round.getCurrentTurnPlayer());
        round.setNextActor(nextActorUsername);
        room.setCurrentActorUsername(nextActorUsername);
        round.setType("ACTOR");

        round.setFirst(false); // 下一人不带牌权
        room.setFirst(false);
        return round;
    }

    @PostMapping("/rooms/{roomId}/actor")
    public ResponseEntity<String> handleGameActor(@PathVariable String roomId, @RequestBody GameRound round){
        List<Card> cards = round.getPlayerCards();

        if(GameUtils.check(cards, roomService.getRoom(roomId).getLastCards())){
            GameUtils.removeCards(roomService.getRoom(roomId).getPlayersCards().get(round.getCurrentTurnPlayer()), cards);
            return ResponseEntity.ok("ok");
        }

        // TODO: 发布前一定删除此段代码
//        if(true){
//            return ResponseEntity.ok("ok");
//        }

        return ResponseEntity.ok("你选择的牌不符合规则!");
    }

    /**
     * PASS
     */
    @MessageMapping("/rooms/{roomId}/pass")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGamePass(@DestinationVariable String roomId, GameRound round){
        Room room = roomService.getRoom(roomId);

        round.setType("PASS");

        String nextActorUsername = gameService.nextActor(roomId, round.getCurrentTurnPlayer());
        round.setNextActor(nextActorUsername);
        room.setCurrentActorUsername(nextActorUsername);
        round.setPlayerCards(null);

        // 下一出牌人 与 上一出牌人为同一人时 形成牌权
        if(round.getNextActor().equals(room.getLastActorUsername())) {
            round.setFirst(true);
            room.setLastCards(null);
            room.setFirst(true);
        }else{
            round.setFirst(false);
            room.setFirst(false);
        }

        return round;
    }

    /**
     * 接受玩家手牌出空请求
     */
    @MessageMapping("/rooms/{roomId}/hand-empty")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameHandEmptyRequest(@DestinationVariable String roomId, GameRound round){

        Room room = roomService.getRoom(roomId);
        String username = round.getCurrentTurnPlayer();

        room.getHandEmptyMap().put(username, true);
        Integer size = room.getPlayersSettlementSequenceMap().size() + 1;
        room.getPlayersSettlementSequenceMap().put(username, size);
        room.getPlayersSettlementSequencesMap().put(size, username);

        if(room.getPlayersSettlementSequenceMap().size() >= 4 || gameService.checkGameIsOver(roomId)){ // 有4名玩家出空 游戏结束
            Set<User> s = room.getPlayers();
            Map<String, Integer> scoreMap;
            Map<String, Integer> nicknameWithScoreMap = new HashMap<>();
            Map<String, Integer> totalScoreMap = room.getTotalScoreMap();
            Map<String, Integer> nicknameWithTotalScoreMap = new HashMap<>();

            logger.info(totalScoreMap);

            for(User user : s){
                if(!room.getPlayersSettlementSequenceMap().containsKey(user.getUsername())){
                    Integer cap = room.getPlayersSettlementSequenceMap().size() + 1;
                    room.getPlayersSettlementSequenceMap().put(user.getUsername(), cap);
                    room.getPlayersSettlementSequencesMap().put(cap, user.getUsername());
                }
            }

            round.setType("GAME_OVER");
            scoreMap = gameService.calculateResult(roomId);

            for(User user : s){
                String name = user.getUsername();
                userService.updateUserScoreInfo(name, scoreMap.get(name));
                totalScoreMap.put(name, totalScoreMap.get(name) + scoreMap.get(name));
                nicknameWithScoreMap.put(user.getNickname(), scoreMap.get(name));
                nicknameWithTotalScoreMap.put(user.getNickname(), totalScoreMap.get(name));
            }

            round.setScoreMap(nicknameWithScoreMap);
            round.setTotalScoreMap(nicknameWithTotalScoreMap);
            logger.info(scoreMap);
            gameService.restartGame(roomId);
            return round;
        }

        room.setLastActorUsername(gameService.nextActor(roomId, username)); // 给风

        round.setType("HAND_EMPTY");
        round.setActor(username);
        round.setTeam(room.getPlayersTeamMap().get(username));
        round.setOrder(room.getPlayersSettlementSequenceMap().get(username));
        return round;
    }

    /**
     * restart 重新对局
     */
    @MessageMapping("/rooms/{roomId}/restart")
    @SendTo("/topic/rooms.{roomId}")
    public GameRound handleGameRestart(@DestinationVariable String roomId, @RequestBody GameRound round){
        Map<String, List<Card>> playerCards = gameService.getRoomCards(roomId);
        GameUtils.sortPlayerCards(playerCards);
        round.setType("GAME_RESTART");
        return round;
    }

    /**
     * recover 重连恢复
     */
    @PostMapping("/rooms/{roomId}/recover")
    @ResponseBody
    public GameRound handleGameRecover(@PathVariable String roomId, @RequestBody String username){
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
