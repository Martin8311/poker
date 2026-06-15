package martin.game.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 玩家类：绑定用户与座位信息
 */
@Data
@NoArgsConstructor
public class Player {
    private User user;
    private SeatType seatType; // 座位号码
    private boolean isReady; //准备状态

    // 构造方法
    public Player(User user, SeatType seatType) {
        this.user = user;
        this.seatType = seatType;
        this.isReady = false;
    }
}
