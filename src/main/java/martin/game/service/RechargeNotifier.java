package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.dto.PaymentNotifyMessageDto;
import martin.game.model.RechargeOrder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 充值状态变更时通过 STOMP 推送到 /user/{username}/queue/recharge。
 *
 * <p>Spring Security + STOMP 集成下，{@code convertAndSendToUser} 会自动按
 * 当前会话 Principal 分发到该用户的 queue，前端订阅即可收到。</p>
 */
@Service
@RequiredArgsConstructor
public class RechargeNotifier {

    private static final Logger logger = LogManager.getLogger(RechargeNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyPaid(String username, RechargeOrder order, int daysAdded, String newExpireAt) {
        send(username, new PaymentNotifyMessageDto(
                "PAID",
                order.getId(),
                order.getOrderNo(),
                order.getRole(),
                daysAdded,
                newExpireAt,
                "开通成功！角色：" + order.getRole() + "，" + daysAdded + " 天已到账"
        ));
    }

    public void notifyFailed(String username, RechargeOrder order, String reason) {
        send(username, new PaymentNotifyMessageDto(
                "FAILED",
                order.getId(),
                order.getOrderNo(),
                null, 0, null,
                "支付失败：" + reason
        ));
    }

    public void notifyCancelled(String username, RechargeOrder order) {
        send(username, new PaymentNotifyMessageDto(
                "CANCELLED",
                order.getId(),
                order.getOrderNo(),
                null, 0, null,
                "订单已取消"
        ));
    }

    private void send(String username, PaymentNotifyMessageDto msg) {
        if (username == null) {
            logger.warn("STOMP 推送跳过（username 为空）: {}", msg);
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(username, "/queue/recharge", msg);
            logger.info("STOMP 推送: user={}, status={}, orderNo={}", username, msg.getStatus(), msg.getOrderNo());
        } catch (Exception e) {
            logger.warn("STOMP 推送失败（不影响主流程）: user={}, err={}", username, e.getMessage());
        }
    }
}
