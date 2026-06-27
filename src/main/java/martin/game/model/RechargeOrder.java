package martin.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 充值订单。状态机：CREATED / PENDING_PAYMENT / PAID / FAILED / EXPIRED / CANCELLED。
 */
@Entity
@Table(
        name = "recharge_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_no", columnNames = "order_no"),
        indexes = {
                @Index(name = "idx_user_time", columnList = "username, create_time"),
                @Index(name = "idx_plan", columnList = "plan_id"),
                @Index(name = "idx_status_expired", columnList = "status, expired_at")
        }
)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RechargeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String username;

    /** 业务订单号 RCH20260627xxxx，唯一 */
    @Column(name = "order_no", length = 64)
    private String orderNo;

    @Column(name = "plan_id", nullable = false, length = 32)
    private String planId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false)
    private Integer days;

    @Column(name = "price_fen", nullable = false)
    private Long priceFen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status = OrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    private PaymentMethod paymentMethod;

    /** 第三方支付流水号（mock 也生成 UUID） */
    @Column(name = "payment_no", length = 64)
    private String paymentNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 订单过期时间（创建时间 + ttl-minutes） */
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = OrderStatus.CREATED;
        }
    }
}
