package martin.game.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import martin.game.model.OrderStatus;
import martin.game.model.PaymentMethod;
import martin.game.model.RechargeOrder;
import martin.game.repository.RechargeOrderRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付宝支付网关。
 *
 * <p>沙箱（默认）/ 生产可切换；回调验签使用支付宝公钥（沙箱/生产各自不同）。</p>
 *
 * <p>使用条件：仅当 {@code recharge.gateway=alipay} 时才注册为 Bean，避免无密钥时启动失败。</p>
 */
@Component
@ConditionalOnProperty(name = "recharge.gateway", havingValue = "alipay")
@RequiredArgsConstructor
public class AlipayPaymentGateway implements PaymentGateway {

    private static final Logger logger = LogManager.getLogger(AlipayPaymentGateway.class);

    @Value("${recharge.gateway.alipay.app-id}")
    private String appId;

    @Value("${recharge.gateway.alipay.private-key}")
    private String privateKey;

    @Value("${recharge.gateway.alipay.public-key}")
    private String alipayPublicKey;

    @Value("${recharge.gateway.alipay.sandbox:true}")
    private boolean sandbox;

    @Value("${recharge.gateway.alipay.notify-url-prefix}")
    private String notifyUrlPrefix;

    private final RechargeOrderRepository orderRepository;
    private AlipayClient alipayClient;

    @PostConstruct
    public void init() {
        try {
            AlipayConfig config = new AlipayConfig();
            config.setServerUrl(sandbox ? "https://openapi.alipaydev.com/gateway.do"
                                        : "https://openapi.alipay.com/gateway.do");
            config.setAppId(appId);
            config.setPrivateKey(privateKey);
            config.setFormat("json");
            config.setCharset("UTF-8");
            config.setSignType("RSA2");
            config.setAlipayPublicKey(alipayPublicKey);
            this.alipayClient = new DefaultAlipayClient(config);
            logger.info("Alipay 网关初始化完成: sandbox={}, appId={}", sandbox, appId);
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay 客户端初始化失败（请检查 AppID / 私钥格式）", e);
        }
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.ALIPAY;
    }

    @Override
    public GatewayOrder createOrder(RechargeOrder order, String notifyUrl) {
        try {
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(order.getOrderNo());
            model.setTotalAmount(String.format("%.2f", order.getPriceFen() / 100.0));
            model.setSubject("Poker VIP " + order.getDays() + " days");
            model.setProductCode("FAST_INSTANT_TRADE_PAY");

            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setBizModel(model);
            request.setNotifyUrl(notifyUrl);
            request.setReturnUrl(notifyUrlPrefix + "/hall"); // 支付完跳回大厅

            String payUrl = alipayClient.pageExecute(request).getBody();

            order.setPaymentMethod(PaymentMethod.ALIPAY);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            orderRepository.save(order);

            logger.info("Alipay 统一下单成功: orderNo={}, payUrl.len={}", order.getOrderNo(), payUrl.length());
            return new GatewayOrder(order.getOrderNo(), payUrl, null, null, null);
        } catch (AlipayApiException e) {
            logger.error("Alipay 统一下单失败: orderNo={}", order.getOrderNo(), e);
            throw new IllegalStateException("Alipay 统一下单失败: " + e.getMessage());
        }
    }

    @Override
    public PaymentNotifyResult parseNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) params.put(k, v[0]);
        });
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");
        String tradeStatus = params.get("trade_status");

        PaymentNotifyResult r = new PaymentNotifyResult();
        r.setOutTradeNo(outTradeNo);
        r.setGatewayOrderNo(tradeNo);
        r.setStatus("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus) ? "PAID" : "FAILED");
        if (totalAmount != null) {
            r.setAmount(Math.round(Double.parseDouble(totalAmount) * 100));
        }
        r.setRawParams(params);
        return r;
    }

    @Override
    public boolean verifyNotify(PaymentNotifyResult result) {
        try {
            return AlipaySignature.rsaCheckV1(
                    result.getRawParams(),
                    alipayPublicKey,
                    "UTF-8",
                    "RSA2"
            );
        } catch (AlipayApiException e) {
            logger.error("Alipay 验签失败", e);
            return false;
        }
    }
}
