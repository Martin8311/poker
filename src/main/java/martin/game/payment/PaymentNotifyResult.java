package martin.game.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调统一结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotifyResult {

    /** 第三方流水号 */
    private String gatewayOrderNo;

    /** 我方业务订单号 */
    private String outTradeNo;

    /** 实付金额（分） */
    private long amount;

    /** 回调状态：PAID / FAILED */
    private String status;

    /** 失败原因（status=FAILED 时） */
    private String reason;

    /** 原始参数（用于验签 / 调试） */
    private Map<String, String> rawParams = new HashMap<>();
}
