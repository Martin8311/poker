package martin.game.service;

import martin.game.model.RechargeProperties;
import martin.game.model.RechargeOrder;
import martin.game.model.OrderStatus;
import martin.game.model.PaymentMethod;
import martin.game.model.Role;
import martin.game.model.User;
import martin.game.payment.MockPaymentGateway;
import martin.game.payment.PaymentGatewayRouter;
import martin.game.repository.RechargeOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RechargeServiceTest {

    @Mock
    private RechargeOrderRepository orderRepository;
    @Mock
    private UserService userService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private PaymentGatewayRouter gatewayRouter;
    @Mock
    private MockPaymentGateway mockPaymentGateway;
    @Mock
    private RechargeNotifier notifier;

    private RechargeService rechargeService;

    @BeforeEach
    void setUp() {
        rechargeService = new RechargeService(
                new RechargeProperties(),
                orderRepository,
                userService,
                redisTemplate,
                gatewayRouter,
                mockPaymentGateway,
                notifier);
    }

    @Test
    @DisplayName("grantVip 从当前有效期后续期")
    void grantVipExtendsFromCurrentExpiry() {
        User user = new User();
        user.setUsername("alice");
        user.setRole(Role.VIP);
        user.setVipExpireAt(LocalDateTime.now().plusDays(10));
        when(userService.findByUsername("alice")).thenReturn(user);

        String newExpireAt = rechargeService.grantVip("alice", Role.VIP, 30);

        LocalDateTime parsed = LocalDateTime.parse(newExpireAt);
        assertThat(parsed).isAfter(LocalDateTime.now().plusDays(39));
        verify(userService).setRole("alice", Role.VIP, parsed);
    }

    @Test
    @DisplayName("grantVip 不会把 SVIP 降级成 VIP")
    void grantVipDoesNotDowngradeSvip() {
        User user = new User();
        user.setUsername("alice");
        user.setRole(Role.SVIP);
        user.setVipExpireAt(LocalDateTime.now().plusDays(5));
        when(userService.findByUsername("alice")).thenReturn(user);

        String newExpireAt = rechargeService.grantVip("alice", Role.VIP, 30);

        verify(userService).setRole("alice", Role.SVIP, LocalDateTime.parse(newExpireAt));
    }

    @Test
    @DisplayName("getStatus 返回有效角色和剩余天数")
    void getStatusReturnsEffectiveRole() {
        RechargeOrder order = mock(RechargeOrder.class);
        User user = new User();
        user.setUsername("alice");
        user.setRole(Role.VIP);
        user.setVipExpireAt(LocalDateTime.now().plusHours(30));
        when(userService.findByUsername("alice")).thenReturn(user);
        when(orderRepository.countByUsername("alice")).thenReturn(1L);
        when(orderRepository.findByUsernameOrderByCreateTimeDesc("alice")).thenReturn(java.util.List.of(order));
        when(order.getDays()).thenReturn(30);

        var status = rechargeService.getStatus("alice");

        assertThat(status.getRole()).isEqualTo("VIP");
        assertThat(status.getDaysLeft()).isGreaterThanOrEqualTo(2);
        assertThat(status.getTotalOrders()).isEqualTo(1);
        assertThat(status.getTotalRechargedDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("历史 SUCCESS 订单详情对前端规范化为 PAID")
    void legacySuccessOrderDetailExposesPaid() {
        RechargeOrder order = new RechargeOrder();
        order.setId(10L);
        order.setUsername("alice");
        order.setOrderNo("RCH-legacy");
        order.setPlanId("VIP_30");
        order.setRole("VIP");
        order.setDays(30);
        order.setPriceFen(1000L);
        order.setStatus(OrderStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.MOCK);
        order.setCreateTime(LocalDateTime.now());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        var detail = rechargeService.getOrder("alice", 10L);

        assertThat(detail.getStatus()).isEqualTo("PAID");
    }
}
