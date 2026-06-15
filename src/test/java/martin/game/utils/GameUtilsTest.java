package martin.game.utils;

import martin.game.model.Card;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * GameUtils 牌型规则单元测试（纯逻辑，无 Spring 上下文 / 外部依赖）。
 *
 * 牌力值约定：5=1, 6=2, …, A=10, 2=11, 3=4=12, 小王=13, 大王=14
 */
class GameUtilsTest {

    private static Card card(String suit, String rank, int value) {
        return new Card(suit + rank, suit + rank, suit, rank, value);
    }

    private static Card joker(int value) {
        String name = value >= 14 ? "JokerA" : "JokerB";
        return new Card(name, name, name, "j", value);
    }

    private static List<Card> cards(Card... cs) {
        return new ArrayList<>(Arrays.asList(cs));
    }

    @Nested
    @DisplayName("checkValue：点数一致性校验")
    class CheckValue {

        @Test
        @DisplayName("单张始终合法")
        void singleCard() {
            assertThat(GameUtils.checkValue(cards(card("Spade", "5", 1)))).isTrue();
        }

        @Test
        @DisplayName("相同点数的对子合法")
        void pairSameRank() {
            assertThat(GameUtils.checkValue(cards(
                    card("Spade", "5", 1), card("Heart", "5", 1)))).isTrue();
        }

        @Test
        @DisplayName("不同点数的两张不合法")
        void twoDifferentRanks() {
            assertThat(GameUtils.checkValue(cards(
                    card("Spade", "5", 1), card("Heart", "6", 2)))).isFalse();
        }

        @Test
        @DisplayName("相同点数的三条合法")
        void tripleSameRank() {
            assertThat(GameUtils.checkValue(cards(
                    card("Spade", "5", 1), card("Heart", "5", 1), card("Club", "5", 1)))).isTrue();
        }

        @Test
        @DisplayName("三张王视为合法（牌值均大于 12）")
        void threeJokers() {
            assertThat(GameUtils.checkValue(cards(
                    joker(14), joker(14), joker(13)))).isTrue();
        }

        @Test
        @DisplayName("四张王视为合法")
        void fourJokers() {
            assertThat(GameUtils.checkValue(cards(
                    joker(14), joker(14), joker(13), joker(13)))).isTrue();
        }

        @Test
        @DisplayName("三张中混入不同点数不合法")
        void tripleWithDifferentRank() {
            assertThat(GameUtils.checkValue(cards(
                    card("Spade", "5", 1), card("Heart", "5", 1), card("Spade", "6", 2)))).isFalse();
        }
    }

    @Nested
    @DisplayName("check：牌权出牌（无上家牌）")
    class CheckLead {

        @Test
        @DisplayName("合法单张可出")
        void legalSingle() {
            assertThat(GameUtils.check(cards(card("Spade", "5", 1)), null)).isTrue();
        }

        @Test
        @DisplayName("合法对子可出")
        void legalPair() {
            assertThat(GameUtils.check(cards(
                    card("Spade", "5", 1), card("Heart", "5", 1)), null)).isTrue();
        }

        @Test
        @DisplayName("点数不同的牌不可出")
        void illegalMixed() {
            assertThat(GameUtils.check(cards(
                    card("Spade", "5", 1), card("Heart", "6", 2)), null)).isFalse();
        }

        @Test
        @DisplayName("空牌不可出")
        void empty() {
            assertThat(GameUtils.check(cards(), null)).isFalse();
        }

        @Test
        @DisplayName("超过 8 张不可出")
        void tooMany() {
            Card[] nine = new Card[9];
            for (int i = 0; i < 9; i++) {
                nine[i] = card("Spade", "5", 1);
            }
            assertThat(GameUtils.check(cards(nine), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("check：跟牌（有上家牌）")
    class CheckFollow {

        @Test
        @DisplayName("同张数且点数更大可压")
        void biggerBeats() {
            List<Card> last = cards(card("Spade", "5", 1), card("Heart", "5", 1));
            List<Card> play = cards(card("Spade", "8", 4), card("Heart", "8", 4));
            assertThat(GameUtils.check(play, last)).isTrue();
        }

        @Test
        @DisplayName("同张数但点数更小不可压")
        void smallerFails() {
            List<Card> last = cards(card("Spade", "8", 4), card("Heart", "8", 4));
            List<Card> play = cards(card("Spade", "5", 1), card("Heart", "5", 1));
            assertThat(GameUtils.check(play, last)).isFalse();
        }

        @Test
        @DisplayName("同张数且点数相等不可压")
        void equalFails() {
            List<Card> last = cards(card("Spade", "5", 1), card("Heart", "5", 1));
            List<Card> play = cards(card("Club", "5", 1), card("Diamond", "5", 1));
            assertThat(GameUtils.check(play, last)).isFalse();
        }

        @Test
        @DisplayName("张数与上家不同不可压")
        void differentCount() {
            List<Card> last = cards(card("Spade", "6", 2), card("Heart", "6", 2));
            List<Card> play = cards(card("Spade", "8", 4));
            assertThat(GameUtils.check(play, last)).isFalse();
        }

        @Test
        @DisplayName("跟牌自身点数不一致不可压")
        void followNotSameRank() {
            List<Card> last = cards(card("Spade", "5", 1), card("Heart", "5", 1));
            List<Card> play = cards(card("Spade", "8", 4), card("Heart", "9", 5));
            assertThat(GameUtils.check(play, last)).isFalse();
        }
    }

    @Nested
    @DisplayName("removeCards：按 name 从手牌移除")
    class RemoveCards {

        @Test
        @DisplayName("移除手牌中匹配的牌")
        void removeMatching() {
            List<Card> hand = cards(
                    card("Spade", "5", 1), card("Heart", "5", 1), card("Spade", "6", 2));
            GameUtils.removeCards(hand, cards(card("Spade", "5", 1)));
            assertThat(hand).hasSize(2)
                    .extracting(Card::getName).containsExactly("Heart5", "Spade6");
        }

        @Test
        @DisplayName("移除不存在的牌时手牌不变")
        void removeNonexistent() {
            List<Card> hand = cards(card("Spade", "5", 1), card("Heart", "5", 1));
            GameUtils.removeCards(hand, cards(card("Club", "9", 5)));
            assertThat(hand).hasSize(2);
        }

        @Test
        @DisplayName("参数为 null 时不抛异常且手牌不变")
        void nullSafe() {
            List<Card> hand = cards(card("Spade", "5", 1));
            assertThatCode(() -> {
                GameUtils.removeCards(null, cards(card("Spade", "5", 1)));
                GameUtils.removeCards(hand, null);
            }).doesNotThrowAnyException();
            assertThat(hand).hasSize(1);
        }
    }

    @Nested
    @DisplayName("sortPlayerCards：手牌排序")
    class SortPlayerCards {

        @Test
        @DisplayName("按牌值降序排列")
        void sortByValueDesc() {
            Map<String, List<Card>> map = new HashMap<>();
            map.put("alice", cards(
                    card("Spade", "5", 1), card("Spade", "A", 10), card("Spade", "K", 9)));
            GameUtils.sortPlayerCards(map);
            assertThat(map.get("alice")).extracting(Card::getValue)
                    .containsExactly(10, 9, 1);
        }

        @Test
        @DisplayName("传入 null 不抛异常")
        void nullSafe() {
            assertThatCode(() -> GameUtils.sortPlayerCards(null))
                    .doesNotThrowAnyException();
        }
    }
}
