package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 充值档位 DTO（前端展示用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargePlanDto {
    /** 配置键，如 "VIP_30" */
    private String planId;
    /** 角色名：VIP / SVIP */
    private String role;
    /** 开通天数 */
    private int days;
    /** 价格（分） */
    private long priceFen;
    /** 前端展示用：分 → 元字符串（如 "¥10.00"） */
    private String priceDisplay;
    /** 配置中的 label 原文 */
    private String label;
    /** 是否为推荐档（90 天档） */
    private boolean recommended;
}
