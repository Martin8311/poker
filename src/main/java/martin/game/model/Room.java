package martin.game.model;

import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Data
public class Room {
    private final String roomId; //房间唯一标识
    private final User creator; // 房主
    private final Set<User> players;
    private Integer readyNumOfPlayers = 0; // 已准备人数。

    private Map<String, SeatType> seatTypePlayerMap = new HashMap<>();
    private Map<String, Boolean> handEmptyMap = new HashMap<>();
    private Map<String, Integer> playersTeamMap = new HashMap<>();  // 0 好人 1 small 2 big
    private Map<String, Integer> playersSettlementSequenceMap = new HashMap<>();
    private Map<Integer, String> playersSettlementSequencesMap = new HashMap<>();

    private boolean gameStarted = false; // 游戏状态
    private final ReadWriteLock playersLock = new ReentrantReadWriteLock();

    private String currentActorUsername = null; //当前正在出牌玩家
    private List<Card> lastCards = null;  // 上手出牌
    private String lastActorUsername = null;  // 上手出牌玩家
    private boolean isFirst = false;

    private Map<String, List<Card>> playersCards; // 保存玩家手中所剩的牌。
    private String bigGhostPlayerUsername;
    private Map<String, Integer> scoreMap = new HashMap<>();
    private Map<String, Integer> totalScoreMap = new HashMap<>();

    private String info = "无";  // 房间简介
    private String roomPassword = null; // 房间上锁
    private boolean publicRoom = true;

    private static final Logger logger = LogManager.getLogger(Room.class);

    // 重置房间
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

    // 游戏状态相关字段
    private GameState gameState;

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
     * 添加玩家（线程安全）
     */
    public boolean addPlayer(User user) {
        playersLock.writeLock().lock();
        try {
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
        } finally {
            playersLock.writeLock().unlock();
        }
    }

    /**
     * 移除玩家（线程安全）
     */
    public boolean removePlayer(User user) {
        playersLock.writeLock().lock();
        try {
            seatTypePlayerMap.entrySet().removeIf(entry -> entry.getKey().equals(user.getUsername()));
            playersTeamMap.entrySet().removeIf(entry -> entry.getKey().equals(user.getUsername()));
            totalScoreMap.entrySet().removeIf(entry -> entry.getKey().equals(user.getUsername()));
            scoreMap.entrySet().removeIf(entry -> entry.getKey().equals(user.getUsername()));

            Iterator<User> iterator = players.iterator();
            while(iterator.hasNext()){
                User u = iterator.next();
                if (u.getUsername().equals(user.getUsername())) {
                    iterator.remove(); // 使用迭代器的remove方法安全删除
                    return true; // 找到并删除后可退出循环（如果用户名唯一）
                }
            }

        } finally {
            playersLock.writeLock().unlock();
        }
        return false;
    }

    /**
     * 获取玩家列表（线程安全）
     */
    public Set<User> getPlayers() {
        playersLock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(players));
        } finally {
            playersLock.readLock().unlock();
        }
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
