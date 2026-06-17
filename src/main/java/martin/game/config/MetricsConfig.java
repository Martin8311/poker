package martin.game.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import martin.game.repository.RoomRepository;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义业务指标。
 *
 * <p>把"当前在线房间数"注册成一个 Micrometer Gauge，随其余指标一起从
 * {@code /actuator/prometheus} 暴露出去，供 Prometheus 抓取、Grafana 展示。</p>
 *
 * <p>用独立配置类在启动时注册，不侵入任何业务逻辑：Prometheus 每次抓取时回调
 * {@link RoomRepository#allRoomIds()} 读取 Redis 房间索引的当前大小。</p>
 */
@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry, RoomRepository roomRepository) {
        Gauge.builder("poker.rooms.active", roomRepository, r -> r.allRoomIds().size())
                .description("Number of active game rooms currently stored in Redis")
                .register(registry);
    }
}
