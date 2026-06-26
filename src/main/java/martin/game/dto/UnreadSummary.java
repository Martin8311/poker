package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 未读私信汇总：总数 + 每个好友各自的未读数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnreadSummary {
    private long total;
    private Map<String, Long> byFriend;
}
