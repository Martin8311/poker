package martin.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 房间（含房间元数据 + 对局状态）。
 *
 * <p>该对象会被序列化存入 Redis（多实例共享）。并发安全不再依赖对象内部的本地锁，
 * 而是由 {@code RoomService.executeWithLock} 的 Redisson 分布式锁在外层串行化保证。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Room {
    private String roomId; // 房间唯一标识
    private User creator;  // 房主
    private Set<User> players = new HashSet<>();
    private Integer readyNumOfPlayers = 0; // 已准备人数

    private Map<String, SeatType> seatTypePlayerMap = new HashMap<>();
    private Map<String, Boolean> handEmptyMap = new HashMap<>();
    private Map<String, Integer> playersTeamMap = new HashMap<>();  // 0 好人 1 small 2 big
    private Map<String, Integer> playersSettlementSequenceMap = new HashMap<>();
    private Map<Integer, String> playersSettlementSequencesMap = new HashMap<>();

    private boolean gameStarted = false; // 游戏状态

    private String currentActorUsername = null; // 当前正在出牌玩家
    private List<Card> lastCards = null;  // 上手出牌
    private String lastActorUsername = null;  // 上手出牌玩家
    private boolean isFirst = false;

    private Map<String, List<Card>> playersCards; // 玩家手中所剩的牌
    private String bigGhostPlayerUsername;
    private Map<String, Integer> scoreMap = new HashMap<>();
    private Map<String, Integer> totalScoreMap = new HashMap<>();

    private String info = "无";  // 房间简介
    private String roomPassword = null; // 房间上锁
    private boolean publicRoom = true;

    private GameState gameState;

    private static final Logger logger = LogManager.getLogger(Room.class);

    // 重置房间（重开一局）
    public void restart(){
        handEmptyMap = new HashMap<>();
        playersTeamMap = new HashMap<>();
        playersSettlementSequenceMap = new HashMap<>();
        playersSettlementSequencesMap = new HashMap<>();
        playersCards = null;
        lastCards = null;
        lastActorUsername = null;
        bigGhostPlayerUsername = null;
        gameStarted = false;
    }

    public Room(String roomId, User creator) {
        this.roomId = roomId;
        this.creator = creator;
        this.players = new HashSet<>();
        this.players.add(creator); // 创建者自动加入房间
        SeatType seat = allocateSeat();
        seatTypePlayerMap.put(creator.getUsername(), seat);
        playersTeamMap.put(creator.getUsername(), 0);
        handEmptyMap.put(creator.getUsername(), false);
        scoreMap.put(creator.getUsername(), 0);
        totalScoreMap.put(creator.getUsername(), 0);
        this.gameStarted = false;
    }

    /**
     * 添加玩家。并发安全由 RoomService 的分布式锁在外层保证。
     */
    public boolean addPlayer(User user) {
        if (players.size() < 5 && !players.contains(user)) {
            SeatType seat = allocateSeat();
            seatTypePlayerMap.put(user.getUsername(), seat);
            playersTeamMap.put(user.getUsername(), 0);
            handEmptyMap.put(user.getUsername(), false);
            scoreMap.put(user.getUsername(), 0);
            totalScoreMap.put(user.getUsername(), 0);
            return players.add(user);
        }
        return false;
    }

    /**
     * 移除玩家。并发安全由 RoomService 的分布式锁在外层保证。
     */
    public boolean removePlayer(User user) {
        seatTypePlayerMap.entrySet().removeIf(e -> e.getKey().equals(user.getUsername()));
        playersTeamMap.entrySet().removeIf(e -> e.getKey().equals(user.getUsername()));
        totalScoreMap.entrySet().removeIf(e -> e.getKey().equals(user.getUsername()));
        scoreMap.entrySet().removeIf(e -> e.getKey().equals(user.getUsername()));

        Iterator<User> iterator = players.iterator();
        while (iterator.hasNext()) {
            User u = iterator.next();
            if (u.getUsername().equals(user.getUsername())) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public SeatType allocateSeat(){
        if(!seatTypePlayerMap.containsValue(SeatType.SEAT_1))
            return SeatType.SEAT_1;
        if(!seatTypePlayerMap.containsValue(SeatType.SEAT_2))
            return SeatType.SEAT_2;
        if(!seatTypePlayerMap.containsValue(SeatType.SEAT_3))
            return SeatType.SEAT_3;
        if(!seatTypePlayerMap.containsValue(SeatType.SEAT_4))
            return SeatType.SEAT_4;
        return SeatType.SEAT_5;
    }

    public SeatType getCurrentSeatByUsername(String username){
        return seatTypePlayerMap.get(username);
    }

    public void readyChange(boolean isReady){
        if(isReady) {
            readyNumOfPlayers++;
            if (readyNumOfPlayers >= 5) {
                logger.error("readyChange: readyNumOfPlayers >= 5");
            }
        } else{
            readyNumOfPlayers--;
            if (readyNumOfPlayers < 0) {
                logger.error("readyChange: readyNumOfPlayers < 0");
            }
        }
        logger.info("准备人数:" + (readyNumOfPlayers + 1) + " / 5");
    }
}
