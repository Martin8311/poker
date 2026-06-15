package martin.game.utils;

import martin.game.model.Card;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 处理扑克牌的静态方法
 */
public class GameUtils {
    private static final Logger logger = LogManager.getLogger(GameUtils.class);

    private static final Map<String, Integer> SUIT_PRIORITY = Map.of(
            "Spade", 1,
            "Heart", 2,
            "Diamond", 4,
            "Club", 3,
            "Joker", 5  // 可根据实际规则调整
    );

    /**
     * 对扑克牌进行排序
     */
    public static void sortPlayerCards(Map<String, List<Card>> playerCards){
        if(playerCards == null)
            return ;

        // 遍历每个玩家的手牌列表
        playerCards.forEach((username, cards) -> {
            // 排序：先按牌值(value)升序/降序，再按花色优先级排序
            cards.sort(Comparator.comparingInt(Card::getValue).reversed()  // 第一排序条件：牌值
                    .thenComparing(card -> SUIT_PRIORITY.getOrDefault(card.getSuit(), -1)));  // 第二排序条件：花色
        });
    }

    /**
     * 检查牌的value是否相同
     */
    public static boolean checkValue(List<Card> cards){
        if(cards.size() == 3 || cards.size() == 4){
            boolean allGreaterThan12 = true;
            for(Card card : cards){
                if(card.getValue() <= 12) {
                    allGreaterThan12 = false;
                    break;
                }
            }

            if(allGreaterThan12){ // 3张王 4张王
                return true;
            }
        }

        int baseValue = cards.get(0).getValue();

        // 遍历剩余的牌，与基准值比较
        for (int i = 1; i < cards.size(); i++) {
            Card card = cards.get(i);
            // 若有任何一张牌的value与基准值不同，返回false
            if (card.getValue() != baseValue) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查出牌是否合规
     * cards 欲出牌
     * lastCards 上手牌
     */
    public static boolean check(List<Card> cards, List<Card> lastCards){

        // 出牌数目不符合规则
        if(cards.size() <= 0 || cards.size() > 8){
            return false;
        }

        if(lastCards == null){ // 牌权出牌

            if(!GameUtils.checkValue(cards)){  // 点数不同
                return false;
            }

        }else{
            // 欲出牌 张数与 上手牌 张数不同
            if(cards.size() != lastCards.size())
                return false;

            if(!GameUtils.checkValue(cards)){  // 点数不同
                return false;
            }

            if(cards.get(0).getValue() <= lastCards.get(0).getValue()){ // 没有上手牌点数大
                return false;
            }

        }
        return true;
    }

    /**
     * 从玩家手牌中移除指定的牌（基于name字段匹配）
     * @param playerCards 玩家当前的手牌（会被直接修改）
     * @param removeCards 需要移除的牌列表
     */
    public static void removeCards(List<Card> playerCards, List<Card> removeCards){
        // 防御性检查：避免空指针异常
        if (playerCards == null || playerCards.isEmpty() || removeCards == null || removeCards.isEmpty()) {
            return ;
        }

        // 提取需要移除的卡牌name集合（用于快速匹配）
        List<String> removeNames = new ArrayList<>();
        for (Card card : removeCards) {
            // 过滤无效卡牌（name为null的牌不处理）
            if (card != null && card.getName() != null) {
                removeNames.add(card.getName());
            }
        }

        // 若没有有效的卡牌name，直接返回
        if (removeNames.isEmpty()) {
            return;
        }

        // 迭代移除玩家手牌中匹配的牌
        playerCards.removeIf(card ->
                // 只移除name存在且在待移除列表中的牌
                card != null && card.getName() != null && removeNames.contains(card.getName())
        );

    }
}
