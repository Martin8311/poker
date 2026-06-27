package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.model.OrderStatus;
import martin.game.model.RechargeOrder;
import martin.game.repository.RechargeOrderRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 周期清理过期订单（CREATED / PENDING_PAYMENT 且 expired_at 已过）。
 *
 * <p>每分钟跑一次（可配 {@code recharge.order.cleanup-interval-ms}）。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final Logger logger = LogManager.getLogger(OrderExpirationScheduler.class);

    private final RechargeOrderRepository orderRepository;
    private final RechargeNotifier notifier;

    @Scheduled(fixedDelayString = "${recharge.order.cleanup-interval-ms:60000}", initialDelay = 30000)
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        List<RechargeOrder> expired = orderRepository.findExpiredOrders(
                List.of(OrderStatus.CREATED, OrderStatus.PENDING_PAYMENT), now);
        if (expired.isEmpty()) return;

        for (RechargeOrder o : expired) {
            o.setStatus(OrderStatus.EXPIRED);
        }
        orderRepository.saveAll(expired);
        logger.info("过期订单清理: count={}", expired.size());

        for (RechargeOrder o : expired) {
            notifier.notifyFailed(o.getUsername(), o, "订单超时未支付");
        }
    }
}
