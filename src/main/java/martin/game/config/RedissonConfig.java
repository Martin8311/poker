package martin.game.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义 Redisson 客户端 Bean，覆盖 Redisson Spring Boot Starter 的自动配置。
 *
 * <p>原因：默认自动配置在 {@code spring.data.redis.password} 为空字符串时，
 * 仍会调用 {@code setPassword("")} 并向 Redis 发送 AUTH，导致本机无密码的 Redis 报
 * {@code ERR Client sent AUTH, but no password is set}。</p>
 *
 * <p>本配置在密码为空时<b>不调用</b> {@code setPassword}，兼容本地无密码 Redis 与
 * 线上 {@code REDIS_PASSWORD} 已配置两种场景。</p>
 */
@Configuration
public class RedissonConfig {

    private static final Logger logger = LogManager.getLogger(RedissonConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig single = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setConnectTimeout(5000)
                .setTimeout(3000);

        if (password != null && !password.isEmpty()) {
            single.setPassword(password);
            logger.info("Redisson 已配置密码认证（host={}, port={}）", host, port);
        } else {
            logger.info("Redisson 无密码模式（host={}, port={}）", host, port);
        }

        return Redisson.create(config);
    }
}
