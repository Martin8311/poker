package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 签到动作的返回结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInResultDto {

    /** 业务是否成功（false 时可能已签、锁冲突等） */
    private boolean success;

    /** 今日是否已签（true 表示幂等命中，未做插入） */
    private boolean alreadySigned;

    /** 消息（前端 toast 用） */
    private String message;

    /** 今日签到后的连续天数 */
    private int consecutiveDays;

    /** 累计签到总天数 */
    private long totalDays;

    /** 今日日期 yyyy-MM-dd */
    private String todayDate;
}
