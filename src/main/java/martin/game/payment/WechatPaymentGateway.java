package martin.game.payment;

import jakarta.servlet.http.HttpServletRequest;
import martin.game.model.PaymentMethod;
import martin.game.model.RechargeOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信支付网关 stub。
 *
 * <p>当前不引入 wechatpay SDK；启用需：
 * <ol>
 *     <li>pom.xml 加：{@code com.github.wechatpay-apiv3:wechatpay-java:0.4.x}</li>
 *     <li>本类所有方法去掉 {@code throw new UnsupportedOperationException} 并实现</li>
 *     <li>{@code application.properties} 设 {@code recharge.gateway=wechat}（需先扩展本类注册条件）</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "recharge.gateway", havingValue = "wechat", matchIfMissing = false)
public class WechatPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.WECHAT;
    }

    @Override
    public GatewayOrder createOrder(RechargeOrder order, String notifyUrl) {
        throw new UnsupportedOperationException(
                "WeChat 支付未启用。请在 pom.xml 引入 wechatpay-java SDK 并实现 WechatPaymentGateway。");
    }

    @Override
    public PaymentNotifyResult parseNotify(HttpServletRequest request) {
        throw new UnsupportedOperationException("WeChat 支付未启用。");
    }

    @Override
    public boolean verifyNotify(PaymentNotifyResult result) {
        throw new UnsupportedOperationException("WeChat 支付未启用。");
    }
}
