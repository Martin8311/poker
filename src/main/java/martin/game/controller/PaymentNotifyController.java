package martin.game.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import martin.game.model.PaymentMethod;
import martin.game.payment.PaymentGatewayRouter;
import martin.game.payment.PaymentNotifyResult;
import martin.game.service.RechargeService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付网关异步回调入口。
 *
 * <p>无需登录（Alipay/WeChat 异步 POST，无 session）。</p>
 * <ul>
 *     <li>{@code POST /recharge/notify/alipay} —— 支付宝回调</li>
 *     <li>（未来）{@code POST /recharge/notify/wechat}</li>
 * </ul>
 */
@RestController
@RequestMapping("/recharge/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private static final Logger logger = LogManager.getLogger(PaymentNotifyController.class);

    private final RechargeService rechargeService;
    private final PaymentGatewayRouter gatewayRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/alipay")
    public String alipayNotify(HttpServletRequest request) {
        return handle(request, PaymentMethod.ALIPAY);
    }

    /**
     * 通用处理：解析 → 验签 → 交给 RechargeService。
     */
    private String handle(HttpServletRequest request, PaymentMethod method) {
        try {
            PaymentNotifyResult result = gatewayRouter.byMethod(method).parseNotify(request);
            logger.info("支付回调: method={}, outTradeNo={}, status={}",
                    method, result.getOutTradeNo(), result.getStatus());
            rechargeService.handleNotify(method, result);
            return "success";
        } catch (Exception e) {
            logger.error("支付回调处理失败: method={}", method, e);
            return "fail";
        }
    }
}
