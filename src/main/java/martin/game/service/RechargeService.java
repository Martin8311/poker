package martin.game.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import martin.game.dto.*;
import martin.game.model.*;
import martin.game.payment.GatewayOrder;
import martin.game.payment.MockPaymentGateway;
import martin.game.payment.PaymentGateway;
import martin.game.payment.PaymentGatewayRouter;
import martin.game.payment.PaymentNotifyResult;
import martin.game.repository.RechargeOrderRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 充值服务：拆为 5 步（createOrder / mockPay / grantVip / pollOrder / cancel）。
 *
 * <h3>状态机</h3>
 * <pre>
 * CREATED ──createOrder──> PENDING_PAYMENT ──notify(PAID)──> PAID ──grantVip──> (开通)
 *   │                          │                                  
 *   │                          └─notify(FAILED)──> FAILED         
 *   │                                                          
 *   ├─cancel──> CANCELLED                                      
 *   └─15min───> EXPIRED  (OrderExpirationScheduler 周期清理)   
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class RechargeService {

    private static final Logger logger = LogManager.getLogger(RechargeService.class);

    private final RechargeProperties rechargeProperties;
    private final RechargeOrderRepository orderRepository;
    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final PaymentGatewayRouter gatewayRouter;
    private final MockPaymentGateway mockPaymentGateway;
    private final RechargeNotifier notifier;

    @Value("${recharge.order.ttl-minutes:15}")
    private int orderTtlMinutes;

    @Value("${recharge.mock.delay-ms:1000}")
    private long mockDelayMs;

    private static final String LOCK_KEY_PREFIX = "recharge:lock:";

    // 简单的 mock 异步调度（不引 @EnableAsync，避免污染全局）
    private final ScheduledExecutorService mockScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "recharge-mock-callback");
                t.setDaemon(true);
                return t;
            });

    // ============================================================
    //  列表 / 状态（不变）
    // ============================================================

    public List<RechargePlanDto> listPlans() {
        List<RechargePlanDto> out = new java.util.ArrayList<>();
        for (Map.Entry<String, RechargeProperties.Plan> e : rechargeProperties.sortedPlans()) {
            String planId = e.getKey();
            RechargeProperties.Plan p = e.getValue();
            String role = parseRoleFromPlanId(planId);
            out.add(new RechargePlanDto(planId, role, p.getDays(), p.getPrice(),
                    formatFen(p.getPrice()), p.getLabel(), p.getDays() == 90));
        }
        return out;
    }

    public RechargeStatusDto getStatus(String username) {
        User u = userService.findByUsername(username);
        LocalDateTime now = LocalDateTime.now();
        Role effective = u.getEffectiveRole(now);

        long daysLeft = 0;
        boolean expired = true;
        LocalDateTime expireAt = u.getVipExpireAt();
        if (effective == Role.VIP || effective == Role.SVIP) {
            if (expireAt != null && expireAt.isAfter(now)) {
                Duration d = Duration.between(now, expireAt);
                daysLeft = Math.max(1, (d.toMinutes() + 1439) / 1440);
                expired = false;
            }
        } else {
            expireAt = null;
        }
        long totalOrders = orderRepository.countByUsername(username);
        long totalDays = orderRepository.findByUsernameOrderByCreateTimeDesc(username).stream()
                .mapToLong(RechargeOrder::getDays).sum();

        return new RechargeStatusDto(
                effective.name(), effective.getDisplayName(),
                expireAt == null ? null : expireAt.toString(),
                daysLeft, expired, totalOrders, totalDays);
    }

    // ============================================================
    //  Step 1：创建订单
    // ============================================================

    /**
     * 创建订单：status=CREATED → 调网关下单 → PENDING_PAYMENT。
     */
    @Transactional
    public CreateOrderResultDto createOrder(String username, String planId) {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("档位不能为空");
        }
        RechargeProperties.Plan plan = rechargeProperties.getPlan().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("档位不存在: " + planId);
        }

        // 防重锁 5s
        String lockKey = LOCK_KEY_PREFIX + username;
        if (Boolean.FALSE.equals(tryAcquireLock(lockKey))) {
            throw new IllegalStateException("操作过于频繁，请稍后再试");
        }
        try {
            User u = userService.findByUsername(username);
            if (u.getRole() == Role.ADMIN) {
                throw new IllegalArgumentException("管理员无需充值");
            }
            Role planRole = parseRoleFromPlanIdEnum(planId);

            // 构造订单
            RechargeOrder order = new RechargeOrder();
            order.setUsername(username);
            order.setOrderNo(generateOrderNo());
            order.setPlanId(planId);
            order.setRole(planRole.name());
            order.setDays(plan.getDays());
            order.setPriceFen(plan.getPrice());
            order.setStatus(OrderStatus.CREATED);
            order.setExpiredAt(LocalDateTime.now().plusMinutes(orderTtlMinutes));
            orderRepository.save(order);

            // 调网关下单（gateway.createOrder 内部会改 status=PENDING_PAYMENT 并写 paymentNo）
            String notifyUrl = "/recharge/notify/" + username; // 占位；alipay 会带 host
            PaymentGateway gateway = gatewayRouter.active();
            GatewayOrder go = gateway.createOrder(order, notifyUrl);
            orderRepository.save(order);  // 再 save 一次确保 PENDING_PAYMENT 持久化

            logger.info("订单创建: user={}, orderNo={}, plan={}, method={}",
                    username, order.getOrderNo(), planId, gateway.method());

            return new CreateOrderResultDto(
                    order.getId(),
                    order.getOrderNo(),
                    planId,
                    plan.getLabel(),
                    order.getRole(),
                    plan.getDays(),
                    plan.getPrice(),
                    formatFen(plan.getPrice()),
                    gateway.method().name(),
                    order.getExpiredAt().toString(),
                    go.getPayUrl()
            );
        } finally {
            try { stringRedisTemplate.delete(lockKey); } catch (Exception ignore) {}
        }
    }

    // ============================================================
    //  Step 2：mock 支付
    // ============================================================

    /**
     * Mock 模式下点"确认支付" → 1s 后异步调 grantVip + STOMP 推送。
     */
    @Transactional
    public PaymentResultDto mockPay(String username, Long orderId) {
        RechargeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        if (!order.getUsername().equals(username)) {
            throw new IllegalArgumentException("订单不属于当前用户");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("订单状态非 PENDING_PAYMENT: " + order.getStatus());
        }
        if (order.getPaymentMethod() != PaymentMethod.MOCK) {
            throw new IllegalStateException("非 MOCK 订单，请走真实回调");
        }
        // 异步 1s 后触发 grantVip（不阻塞前端）
        mockScheduler.schedule(
                () -> triggerMockCallback(order.getId()),
                mockDelayMs,
                TimeUnit.MILLISECONDS);

        logger.info("Mock 支付已受理: orderNo={}, delayMs={}", order.getOrderNo(), mockDelayMs);
        return new PaymentResultDto(true, "支付处理中...", order.getId(),
                OrderStatus.PENDING_PAYMENT.name());
    }

    /** 内部：模拟第三方回调成功后真正开通 */
    @Transactional
    public void triggerMockCallback(Long orderId) {
        try {
            RechargeOrder order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                logger.warn("Mock 回调时订单丢失: orderId={}", orderId);
                return;
            }
            if (order.getStatus().isPaidLike()) {
                return; // 幂等
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                logger.warn("Mock 回调时订单非 PENDING_PAYMENT: orderId={}, status={}", orderId, order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);

            int days = order.getDays();
            String newExpireAt = grantVip(order.getUsername(), parseRoleFromPlanIdEnum(order.getPlanId()), days);
            notifier.notifyPaid(order.getUsername(), order, days, newExpireAt);
        } catch (Exception e) {
            logger.error("Mock 回调失败: orderId={}", orderId, e);
        }
    }

    // ============================================================
    //  真实支付回调入口（notify URL）
    // ============================================================

    /**
     * 真实支付网关回调统一入口。
     * @param method 支付方式（URL 段：alipay）
     */
    @Transactional
    public void handleNotify(PaymentMethod method, PaymentNotifyResult result) {
        if (result == null || result.getOutTradeNo() == null) {
            throw new IllegalArgumentException("回调参数缺失");
        }
        // 验签
        PaymentGateway gw = gatewayRouter.byMethod(method);
        if (!gw.verifyNotify(result)) {
            throw new IllegalStateException(method + " 回调验签失败");
        }
        // 查订单
        RechargeOrder order = orderRepository.findByOrderNo(result.getOutTradeNo())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + result.getOutTradeNo()));
        if (order.getStatus().isPaidLike()) {
            return; // 幂等
        }
        if (!"PAID".equals(result.getStatus())) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            notifier.notifyFailed(order.getUsername(), order, result.getReason());
            return;
        }
        // 标记 PAID
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setPaymentNo(result.getGatewayOrderNo());
        orderRepository.save(order);

        // 开通
        String newExpireAt = grantVip(order.getUsername(),
                parseRoleFromPlanIdEnum(order.getPlanId()), order.getDays());
        notifier.notifyPaid(order.getUsername(), order, order.getDays(), newExpireAt);
    }

    // ============================================================
    //  grantVip：把 setRole 逻辑独立出来（被 mockPay 和 notify 共用）
    // ============================================================

    /**
     * 智能续期：newExpireAt = max(now, current) + days；角色取高（不降级）。
     * @return 新过期时间 ISO 字符串（PLAYER 时为 null）
     */
    @Transactional
    public String grantVip(String username, Role planRole, int days) {
        User u = userService.findByUsername(username);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentExpire = u.getVipExpireAt();
        LocalDateTime base = (currentExpire != null && currentExpire.isAfter(now)) ? currentExpire : now;
        LocalDateTime newExpireAt = base.plusDays(days);

        Role currentEffective = u.getEffectiveRole(now);
        Role newRole = currentEffective.isAtLeast(planRole) ? currentEffective : planRole;

        LocalDateTime expireToSet = (newRole == Role.PLAYER) ? null : newExpireAt;
        userService.setRole(username, newRole, expireToSet);
        logger.info("开通成功: user={}, role={}->{}, days={}, newExpireAt={}",
                username, currentEffective, newRole, days, newExpireAt);
        return newExpireAt == null ? null : newExpireAt.toString();
    }

    // ============================================================
    //  轮询
    // ============================================================

    public OrderDetailDto getOrder(String username, Long orderId) {
        RechargeOrder o = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!o.getUsername().equals(username)) {
            throw new IllegalArgumentException("订单不属于当前用户");
        }
        RechargeProperties.Plan plan = rechargeProperties.getPlan().get(o.getPlanId());
        return new OrderDetailDto(
                o.getId(), o.getOrderNo(), o.getPlanId(),
                plan == null ? o.getPlanId() : plan.getLabel(),
                o.getRole(), o.getDays(), o.getPriceFen(),
                o.getStatus().apiName(),
                o.getPaymentMethod() == null ? null : o.getPaymentMethod().name(),
                o.getPaymentNo(),
                o.getPaidAt() == null ? null : o.getPaidAt().toString(),
                o.getExpiredAt() == null ? null : o.getExpiredAt().toString(),
                o.getCreateTime() == null ? null : o.getCreateTime().toString()
        );
    }

    // ============================================================
    //  取消
    // ============================================================

    @Transactional
    public void cancel(String username, Long orderId) {
        RechargeOrder o = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!o.getUsername().equals(username)) {
            throw new IllegalArgumentException("订单不属于当前用户");
        }
        if (o.getStatus() != OrderStatus.CREATED && o.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("订单不可取消: " + o.getStatus());
        }
        o.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(o);
        notifier.notifyCancelled(username, o);
    }

    // ============================================================
    //  工具
    // ============================================================

    private Boolean tryAcquireLock(String key) {
        try {
            return stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(rechargeProperties.getLockTtlSeconds()));
        } catch (Exception e) {
            logger.warn("充值锁获取失败（已降级放行）: {}, err={}", key, e.getMessage());
            return true;
        }
    }

    private static String generateOrderNo() {
        return "RCH" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static String parseRoleFromPlanId(String planId) {
        int i = planId.indexOf('_');
        return i > 0 ? planId.substring(0, i).toUpperCase(Locale.ROOT) : "VIP";
    }

    private static Role parseRoleFromPlanIdEnum(String planId) {
        return Role.valueOf(parseRoleFromPlanId(planId));
    }

    private static String formatFen(long fen) {
        return String.format(Locale.CHINA, "¥%d.%02d", fen / 100, fen % 100);
    }
}
