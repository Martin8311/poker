package martin.game.model;

/**
 * 游戏状态枚举，定义游戏的不同阶段
 */
public enum GameState {
    WAITING_FOR_PLAYERS,  // 等待玩家加入
    READY_TO_START,       // 准备开始（人数已满）
    DEALING_CARDS,        // 发牌中
    GAME_IN_PROGRESS, // 游戏进行中
    PLAYER_TURN,          // 玩家回合
    ROUND_FINISHED,       // 回合结束
    GAME_FINISHED         // 游戏结束
}
