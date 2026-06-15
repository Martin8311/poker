package martin.game.model;

import java.util.List;
import java.util.Map;

/**
 * 游戏动作类，封装玩家在游戏中的各种操作 TODO: 准备弃用
 */

public class GameAction {
    private String type;          // 动作类型：DEAL、PLAY_CARD、PASS、CALL_SCORE等
    private Integer userId;                // 操作玩家ID
    private String username;
    private String userNickname;        // 操作玩家昵称
    private Map<String, Object> data;   // 动作相关数据（如出的牌、叫的分数等）
    private String status;              // 动作处理状态：success、error
    Map<String, List<Card>> playerCards;    // 传递发牌信息用
    private String message;             // 状态描述信息
    private long timestamp;             // 动作时间戳

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    // 构造函数
    public GameAction() {}

    public Map<String, List<Card>> getPlayerCards() {
        return playerCards;
    }

    public void setPlayerCards(Map<String, List<Card>> playerCards) {
        this.playerCards = playerCards;
    }

    public GameAction(String actionType, Map<String, Object> data) {
        this.type = actionType;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // getter和setter
    public String getType() {
        return type;
    }

    public void setType(String actionType) {
        this.type = actionType;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
