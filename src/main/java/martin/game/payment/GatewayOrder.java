package martin.game.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方支付下单结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayOrder {

    /** 第三方订单号（mock 也生成 UUID） */
    private String gatewayOrderNo;

    /** 用户跳转 URL（Alipay 电脑/手机网站支付） */
    private String payUrl;

    /** 二维码 URL（Native 支付，目前未用） */
    private String qrCodeUrl;

    /** 微信 prepay_id（未用） */
    private String prepayId;

    /** 其它扩展信息 */
    private Map<String, String> extra = new HashMap<>();
}
