package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * mock-pay 接口返回。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultDto {
    private boolean success;
    private String message;
    private Long orderId;
    private String status;  // PENDING_PAYMENT（mock 异步回调尚未触发）
}
