package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单详情（含状态、过期时间，前端轮询用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailDto {
    private Long orderId;
    private String orderNo;
    private String planId;
    private String planLabel;
    private String role;
    private int days;
    private long priceFen;
    private String status;
    private String paymentMethod;
    private String paymentNo;
    /** ISO 字符串 */
    private String paidAt;
    /** ISO 字符串 */
    private String expiredAt;
    /** ISO 字符串 */
    private String createTime;
}
