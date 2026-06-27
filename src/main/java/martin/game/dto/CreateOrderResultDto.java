package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单返回结果（含网关下单结果）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResultDto {
    /** 内部订单 ID */
    private Long orderId;
    /** 业务订单号 */
    private String orderNo;
    /** 档位 ID */
    private String planId;
    /** 档位 label */
    private String planLabel;
    /** 角色 */
    private String role;
    /** 天数 */
    private int days;
    /** 价格（分） */
    private long priceFen;
    /** 价格展示 */
    private String priceDisplay;
    /** 支付方式 */
    private String paymentMethod;
    /** 订单过期时间（ISO） */
    private String expiredAt;
    /** 用户跳转 URL（mock 下为 mock-pay URL；alipay 下为支付 URL） */
    private String payUrl;
}
