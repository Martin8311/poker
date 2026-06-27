package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 购买结果 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeResultDto {
    /** 是否成功（业务成功，false 也可能是已开通过等幂等场景） */
    private boolean success;
    /** 消息 */
    private String message;
    /** 开通后角色（PLAYER / VIP / SVIP） */
    private String role;
    /** 开通后过期时间 ISO 字符串（PLAYER 为 null） */
    private String newExpireAt;
    /** 本次新增天数 */
    private int daysAdded;
    /** 本次价格（分） */
    private long priceFen;
    /** 本次订单 ID */
    private Long orderId;
}
