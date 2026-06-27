package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.model.Role;
import martin.game.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把 {@code app.bootstrap-admin-usernames} 配置中的用户提升为 ADMIN（幂等）。
 *
 * <p>典型用法：本地首次部署时把开发账号写进配置；后续用 SQL 提升更直接。</p>
 *
 * <p>同时承担「如果用户不存在则打 warn，不阻断启动」的容错。</p>
 */
@Component
@Order // 顺序无所谓；保留位置
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LogManager.getLogger(AdminBootstrapRunner.class);

    private final UserService userService;

    @Value("${app.bootstrap-admin-usernames:}")
    private List<String> bootstrapAdminUsernames;

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapAdminUsernames == null || bootstrapAdminUsernames.isEmpty()) {
            return;
        }
        for (String raw : bootstrapAdminUsernames) {
            if (raw == null) continue;
            String username = raw.trim();
            if (username.isEmpty()) continue;

            try {
                User u = userService.findByUsername(username);
                if (u.getRole() == Role.ADMIN) {
                    logger.info("Bootstrap admin 已是 ADMIN，跳过: {}", username);
                    continue;
                }
                boolean ok = userService.setRole(username, Role.ADMIN, null);
                if (ok) {
                    logger.info("Bootstrap admin 已提升: {} -> ADMIN", username);
                } else {
                    logger.warn("Bootstrap admin 提升失败（无影响）: {}", username);
                }
            } catch (Exception e) {
                // 用户不存在等情况：仅 warn，不阻断应用启动
                logger.warn("Bootstrap admin 跳过（用户不存在或异常）: {}, err={}", username, e.getMessage());
            }
        }
    }
}
