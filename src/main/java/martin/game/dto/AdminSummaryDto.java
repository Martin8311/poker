package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台首页的 3 个统计数字。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSummaryDto {
    private long totalUsers;
    private long adminCount;
    private long vipCount;     // VIP + SVIP 合计（按 DB 字段，不考虑过期）
}
