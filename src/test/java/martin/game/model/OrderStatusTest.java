package martin.game.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    @DisplayName("历史 SUCCESS 状态按已支付处理，并对前端输出 PAID")
    void legacySuccessIsPaidLike() {
        assertThat(OrderStatus.SUCCESS.isPaidLike()).isTrue();
        assertThat(OrderStatus.SUCCESS.apiName()).isEqualTo("PAID");
        assertThat(OrderStatus.SUCCESS.json()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("JSON 输入 SUCCESS 规范化为 PAID")
    void successJsonInputNormalizesToPaid() {
        assertThat(OrderStatus.fromJson("SUCCESS")).isEqualTo(OrderStatus.PAID);
    }
}
