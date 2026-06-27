package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推送到 /user/{username}/queue/recharge 的消息体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotifyMessageDto {
    /** PAID / FAILED / EXPIRED / CANCELLED */
    private String status;
    private Long orderId;
    private String orderNo;
    /** 开通后的角色（PAID 时） */
    private String role;
    /** 本次新增天数 */
    private int daysAdded;
    /** 新过期时间 ISO 字符串 */
    private String newExpireAt;
    /** 提示文案 */
    private String message;
}
