package martin.game.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 充值档位配置。
 *
 * <p>从 {@code application.properties} 的 {@code recharge.plan.*} 段读取，启动时绑定。
 * 修改后需重启应用生效（不带热加载）。</p>
 */
@ConfigurationProperties(prefix = "recharge")
public class RechargeProperties {

    private Map<String, Plan> plan = new LinkedHashMap<>();

    /** 同一用户开单防重锁 TTL（秒） */
    private int lockTtlSeconds = 5;

    public Map<String, Plan> getPlan() {
        return plan;
    }

    public void setPlan(Map<String, Plan> plan) {
        this.plan = plan;
    }

    public int getLockTtlSeconds() {
        return lockTtlSeconds;
    }

    public void setLockTtlSeconds(int lockTtlSeconds) {
        this.lockTtlSeconds = lockTtlSeconds;
    }

    /**
     * 返回按 planId 升序排列的档位列表（VIP_30 → VIP_90 → VIP_365 → SVIP_*）。
     */
    public List<Map.Entry<String, Plan>> sortedPlans() {
        return plan.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    /** 单个档位配置 */
    public static class Plan {
        private int days;
        private long price;        // 单位：分
        private String label;      // 前端展示文案
        private String role;       // 开通后的目标角色（VIP / SVIP），由 planId 前缀解析时可省略

        public int getDays() { return days; }
        public void setDays(int days) { this.days = days; }
        public long getPrice() { return price; }
        public void setPrice(long price) { this.price = price; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
