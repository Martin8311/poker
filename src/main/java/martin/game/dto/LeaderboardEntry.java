package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排行榜条目（对外返回）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntry {
    private long rank;        // 名次（从 1 开始，-1 表示未上榜）
    private String username;  // 用户名
    private String nickname;  // 昵称
    private String iconUrl;   // 头像 URL
    private long score;       // 累计积分
}
