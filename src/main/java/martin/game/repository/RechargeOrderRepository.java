package martin.game.repository;

import martin.game.model.OrderStatus;
import martin.game.model.RechargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, Long> {

    /** 用户充值历史（按时间倒序） */
    List<RechargeOrder> findByUsernameOrderByCreateTimeDesc(String username);

    /** 用户累计开通天数（用于 status 概览） */
    long countByUsername(String username);

    /** 业务订单号查订单 */
    Optional<RechargeOrder> findByOrderNo(String orderNo);

    /** 清理过期订单：status=PENDING_PAYMENT/CREATED 且 expired_at < now */
    @Query("SELECT o FROM RechargeOrder o WHERE o.status IN :statuses AND o.expiredAt < :now")
    List<RechargeOrder> findExpiredOrders(@Param("statuses") List<OrderStatus> statuses,
                                          @Param("now") LocalDateTime now);
}
