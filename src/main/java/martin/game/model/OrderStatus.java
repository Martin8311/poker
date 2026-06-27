package martin.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 充值订单状态机。
 *
 * <pre>
 * CREATED         下单，尚未发起支付
 * PENDING_PAYMENT 已调起支付，等待用户/回调
 * PAID            支付成功，触发 grantVip
 * FAILED          支付失败（可重试）
 * EXPIRED         超时未付（@Scheduled 清理）
 * CANCELLED       用户主动取消
 * </pre>
 */
public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    /**
     * Legacy paid status kept for rows created by an older version.
     * New code should write PAID and expose PAID to clients.
     */
    SUCCESS,
    FAILED,
    EXPIRED,
    CANCELLED;

    public boolean isPaidLike() {
        return this == PAID || this == SUCCESS;
    }

    public String apiName() {
        return isPaidLike() ? PAID.name() : name();
    }

    @JsonValue
    public String json() {
        return apiName();
    }

    @JsonCreator
    public static OrderStatus fromJson(String value) {
        if (value == null) return null;
        OrderStatus status = OrderStatus.valueOf(value.toUpperCase());
        return status == SUCCESS ? PAID : status;
    }
}
