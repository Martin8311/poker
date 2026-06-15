package martin.game.model;

import lombok.Data;

import java.util.List;
import java.util.Map;


@Data
public class GameRound {
    private String currentTurnPlayer; // 当前回合玩家

    private String actor; // 出牌者 TODO: 可能冗余
    private List<Card> PlayerCards; // 出牌

    private String lastActor; // 上次出牌者
    private List<Card> lastPlayerCards; // 上次出牌
    private Map<String, Integer> scoreMap; // 用于游戏结束后传递分数
    private Map<String, Integer> totalScoreMap; // 用于游戏结束后传递分数

    private Integer team; // 阵营
    private Integer order;  //结算顺序
    private String bigGhostPlayerUsername;

    private Map<String, Integer> playersSettlementSequenceMap;
    private Map<String, Integer> playersTeamMap;

    private String type;
    private String nextActor;
    private boolean isFirst;      // 牌权
    private long timestamp;          // 发送时间戳

}
