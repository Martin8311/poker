package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.*;
import martin.game.service.RechargeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recharge")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService rechargeService;

    @GetMapping("/plans")
    public List<RechargePlanDto> plans() {
        return rechargeService.listPlans();
    }

    @GetMapping("/status")
    public RechargeStatusDto status(Authentication authentication) {
        return rechargeService.getStatus(authentication.getName());
    }

    /** Step 1: 创建订单 */
    @PostMapping("/create-order")
    public CreateOrderResultDto createOrder(Authentication authentication,
                                            @RequestBody Map<String, String> body) {
        String planId = body == null ? null : body.get("planId");
        return rechargeService.createOrder(authentication.getName(), planId);
    }

    /** Step 2: mock 模式下点"确认支付" */
    @PostMapping("/mock-pay/{orderId}")
    public PaymentResultDto mockPay(Authentication authentication,
                                    @PathVariable Long orderId) {
        return rechargeService.mockPay(authentication.getName(), orderId);
    }

    /** 轮询订单状态 */
    @GetMapping("/order/{orderId}")
    public OrderDetailDto order(Authentication authentication,
                                @PathVariable Long orderId) {
        return rechargeService.getOrder(authentication.getName(), orderId);
    }

    /** 取消订单 */
    @PostMapping("/cancel/{orderId}")
    public Map<String, Object> cancel(Authentication authentication,
                                      @PathVariable Long orderId) {
        rechargeService.cancel(authentication.getName(), orderId);
        return Map.of("success", true, "message", "已取消");
    }
}
