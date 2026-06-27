package martin.game.payment;

import lombok.RequiredArgsConstructor;
import martin.game.model.PaymentMethod;
import martin.game.model.RechargeOrder;
import martin.game.model.OrderStatus;
import martin.game.repository.RechargeOrderRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mock 支付网关：跳过真实支付，1 秒后由 RechargeService 模拟回调成功。
 *
 * <p>用于本地开发 / 无商户号时验证完整流程。</p>
 */
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger logger = LogManager.getLogger(MockPaymentGateway.class);

    private final RechargeOrderRepository orderRepository;

    @Value("${recharge.mock.delay-ms:1000}")
    private long mockDelayMs;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.MOCK;
    }

    @Override
    public GatewayOrder createOrder(RechargeOrder order, String notifyUrl) {
        // 写入 paymentNo + 状态切到 PENDING_PAYMENT
        order.setPaymentMethod(PaymentMethod.MOCK);
        order.setPaymentNo("MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        orderRepository.save(order);
        logger.info("Mock 网关下单成功: orderNo={}, paymentNo={}, delayMs={}",
                order.getOrderNo(), order.getPaymentNo(), mockDelayMs);

        // mock 模式下没有真实跳转 URL；前端直接调 mock-pay 接口触发回调
        return new GatewayOrder(
                order.getPaymentNo(),
                "/recharge/mock-pay/" + order.getId(),   // 前端拿到此 URL 直接 POST 即可
                null, null, null
        );
    }

    @Override
    public PaymentNotifyResult parseNotify(jakarta.servlet.http.HttpServletRequest request) {
        // mock 模式：回调由 RechargeService#mockPay 内部完成，不走 HTTP
        throw new UnsupportedOperationException("Mock 模式无 HTTP 回调");
    }

    @Override
    public boolean verifyNotify(PaymentNotifyResult result) {
        return true; // mock 模式天然可信
    }

    public long getMockDelayMs() {
        return mockDelayMs;
    }
}
