package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 月度签到视图：包含当月日历位图与统计信息。
 *
 * <p>前端只需将 {@link #days} 渲染成 6x7 网格即可。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInStatusDto {

    /** 查询的年月 */
    private int year;
    private int month;

    /** 当月所有日期 + 是否已签（按 day 升序） */
    private List<DayStatus> days;

    /** 今日是否已签 */
    private boolean signedToday;

    /** 今日日期（yyyy-MM-dd） */
    private String todayDate;

    /** 累计签到总天数（BITCOUNT） */
    private long totalDays;

    /** 当前连续天数（从今天向前 GETBIT 遇 0 停止） */
    private int consecutiveDays;

    /** 历史最长连续天数（扫描 MySQL 记录计算） */
    private int longestStreak;

    /** 本月已签天数 */
    private long monthDays;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayStatus {
        /** 1-based 当月第几天 */
        private int day;
        /** yyyy-MM-dd */
        private String date;
        /** 是否已签 */
        private boolean signed;
        /** 是否为今天（用于 UI 高亮） */
        private boolean today;
        /** 是否为未来日期（未来日不可点） */
        private boolean future;
        /** LocalDate 字段（便于前端做 Date 对象） */
        private LocalDate rawDate;
    }
}
