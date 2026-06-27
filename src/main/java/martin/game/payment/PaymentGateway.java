package martin.game.payment;

import martin.game.model.PaymentMethod;
import martin.game.model.RechargeOrder;

/**
 * 支付网关抽象接口。
 *
 * <p>三种实现：
 * <ul>
 *     <li>{@link MockPaymentGateway}  —— 默认，无外部依赖，立即成功</li>
 *     <li>{@link AlipayPaymentGateway} —— 沙箱/生产可切换</li>
 *     <li>{@link WechatPaymentGateway} —— stub，需补 SDK</li>
 * </ul>
 */
public interface PaymentGateway {

    /** 网关对应的支付方式 */
    PaymentMethod method();

    /**
     * 调起支付：返回第三方下单结果（跳转 URL / 二维码）。
     *
     * <p>实现方需负责：
     * <ul>
     *     <li>把内部 order 映射为网关订单（生成网关单号）；</li>
     *     <li>构造用户可访问的支付入口（URL 或 qrCode）；</li>
     *     <li>设置 order.status = PENDING_PAYMENT 并写 paymentNo。</li>
     * </ul>
     */
    GatewayOrder createOrder(RechargeOrder order, String notifyUrl);

    /**
     * 解析网关回调请求为统一结构。
     */
    PaymentNotifyResult parseNotify(jakarta.servlet.http.HttpServletRequest request);

    /**
     * 验证回调签名（Alipay 验签 / WeChat 解密 + 验签）。
     */
    boolean verifyNotify(PaymentNotifyResult result);
}
