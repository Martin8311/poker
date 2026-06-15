package martin.game.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * 游戏消息类，用于房间内的聊天和系统通知
 */
@Data
@NoArgsConstructor
public class GameMessage {
    private String content;          // 消息内容
    private String senderNickname;   // 发送者昵称
    private String relatedName;      // 相关用户名字
    private String type;             // 消息类型：SYSTEM 或 USER
    private Set<User> playerList;    //
    private long timestamp;          // 发送时间戳
    private Map<String, SeatType> seatPlayerMap;
    private Integer numOfPlayers;
    private SeatType currentSeat;
    private boolean isReady;

    @Override
    public String toString() {
        return "GameMessage{" +
                "content='" + content + '\'' +
                ", senderNickname='" + senderNickname + '\'' +
                ", relatedName='" + relatedName + '\'' +
                ", type='" + type + '\'' +
                ", playerList=" + playerList +
                ", timestamp=" + timestamp +
                ", seatPlayerMap=" + seatPlayerMap +
                ", numOfPlayers=" + numOfPlayers +
                ", currentSeat=" + currentSeat +
                ", isReady=" + isReady +
                '}';
    }
}
