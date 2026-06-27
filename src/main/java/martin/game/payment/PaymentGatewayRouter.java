package martin.game.payment;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import martin.game.model.PaymentMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关路由器：根据 {@code recharge.gateway} 配置选择具体实现。
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewayRouter {

    private static final Logger logger = LogManager.getLogger(PaymentGatewayRouter.class);

    private final List<PaymentGateway> gateways;

    @Value("${recharge.gateway:mock}")
    private String configured;

    private final Map<PaymentMethod, PaymentGateway> byMethod = new HashMap<>();

    @PostConstruct
    public void init() {
        for (PaymentGateway g : gateways) {
            byMethod.put(g.method(), g);
        }
        logger.info("PaymentGatewayRouter: 已加载网关 {}, 当前激活: {}",
                byMethod.keySet(), configured);
    }

    /**
     * 按当前配置返回网关（mock / alipay）。
     */
    public PaymentGateway active() {
        PaymentMethod m = PaymentMethod.valueOf(configured.toUpperCase());
        PaymentGateway g = byMethod.get(m);
        if (g == null) {
            throw new IllegalStateException("网关未注册: " + m
                    + "（已注册: " + byMethod.keySet() + "）。请检查 pom 依赖与 @ConditionalOnProperty 配置。");
        }
        return g;
    }

    public PaymentGateway byMethod(PaymentMethod m) {
        PaymentGateway g = byMethod.get(m);
        if (g == null) throw new IllegalStateException("网关未注册: " + m);
        return g;
    }
}
