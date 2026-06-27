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
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserSchemaMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LogManager.getLogger(UserSchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("phone_number",
                "ALTER TABLE `user` ADD COLUMN `phone_number` VARCHAR(20) DEFAULT NULL UNIQUE");
        ensureColumn("phone_bound_at",
                "ALTER TABLE `user` ADD COLUMN `phone_bound_at` DATETIME DEFAULT NULL");
    }

    private void ensureColumn(String columnName, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = ?",
                    Integer.class,
                    columnName);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(ddl);
            logger.info("Applied user schema migration: {}", columnName);
        } catch (Exception e) {
            logger.warn("User schema migration skipped for column {}: {}", columnName, e.getMessage());
        }
    }
}
