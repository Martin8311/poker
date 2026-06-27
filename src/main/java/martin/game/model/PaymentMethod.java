package martin.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    MOCK,
    ALIPAY,
    WECHAT;

    @JsonValue
    public String json() {
        return name();
    }

    @JsonCreator
    public static PaymentMethod fromJson(String value) {
        if (value == null) return null;
        return PaymentMethod.valueOf(value.toUpperCase());
    }
}
