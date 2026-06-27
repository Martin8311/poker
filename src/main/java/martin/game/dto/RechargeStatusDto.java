package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户充值状态 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeStatusDto {
    /** 有效角色：PLAYER / VIP / SVIP */
    private String role;
    /** 角色展示名 */
    private String roleDisplay;
    /** 过期时间 ISO 字符串（VIP/SVIP 才有；PLAYER 为 null） */
    private String expireAt;
    /** 剩余天数（向下取整；PLAYER 为 0） */
    private long daysLeft;
    /** 是否已过期（false 表示当前生效中） */
    private boolean expired;
    /** 历史累计开通次数 */
    private long totalOrders;
    /** 历史累计开通天数 */
    private long totalRechargedDays;
}
