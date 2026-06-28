package martin.game.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class ChatModerationSchemaMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LogManager.getLogger(ChatModerationSchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_moderation_log'",
                    Integer.class);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("""
                    CREATE TABLE `chat_moderation_log` (
                      `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
                      `room_id` VARCHAR(64) NOT NULL,
                      `username` VARCHAR(45) NOT NULL,
                      `nickname` VARCHAR(45) DEFAULT NULL,
                      `original_content` VARCHAR(500) NOT NULL,
                      `sanitized_content` VARCHAR(500) DEFAULT NULL,
                      `action` VARCHAR(20) NOT NULL,
                      `reason` VARCHAR(200) DEFAULT NULL,
                      `create_time` DATETIME NOT NULL,
                      KEY `idx_chat_mod_user_time` (`username`, `create_time`),
                      KEY `idx_chat_mod_action_time` (`action`, `create_time`),
                      KEY `idx_chat_mod_room_time` (`room_id`, `create_time`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            logger.info("Applied chat moderation schema migration");
        } catch (Exception e) {
            logger.warn("Chat moderation schema migration skipped: {}", e.getMessage());
        }
    }
}
