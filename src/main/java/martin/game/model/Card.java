package martin.game.model;

import lombok.Data;

/**
 * 扑克牌实体类
 * 包含花色、点数、牌值等核心属性
 */
@Data
public class Card {
    private String name;
    private String url;

    // 花色（♠黑桃、♥红桃、♦方块、♣梅花）
    private String suit;

    // 点数（2-10、J、Q、K、A）
    private String rank;

    // 牌值（用于比较大小，2最小为0，A最大为12）
    private int value;

    /**
     * 构造方法
     * @param suit 花色  club♣ diamond♦ heart♥ spade♠
     * @param rank 点数
     * @param value 牌值
     */
    public Card(String name, String url, String suit, String rank, int value) {
        this.name = name;
        this.url = url;
        this.suit = suit;
        this.rank = rank;
        this.value = value;
    }

    // Getter方法（前端需要通过这些方法获取卡牌信息）
    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }

    public int getValue() {
        return value;
    }

    /**
     * 重写toString方法，方便调试
     */
    @Override
    public String toString() {
        return name + "(" + value + ")";
    }
}
