package martin.game.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 *
 * <p>{@link RedisTemplate} 的 key 用 String 序列化（便于排查），value 用 JSON 序列化。
 * value 的 {@code ObjectMapper} 做了三项关键配置，以支持把 {@code Room}（含 User / Card /
 * LocalDateTime 等）正确存取：
 * <ul>
 *     <li>仅按字段序列化，绕过 {@code UserDetails} 等计算型 getter；</li>
 *     <li>注册 {@code JavaTimeModule}，支持 {@code LocalDateTime}；</li>
 *     <li>开启 default typing（写入 {@code @class}），保证复杂 / 多态对象能正确反序列化。</li>
 * </ul>
 *
 * <p>排行榜（ZSet，member/score 为简单类型）仍直接用 {@code StringRedisTemplate}。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        // 仅按字段序列化，忽略 getter/setter（避免 UserDetails 计算属性等）
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        om.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        // 支持 Java 8 时间类型（如 User.createTime）
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 写入类型信息（@class），保证复杂 / 多态对象反序列化
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return om;
    }
}
