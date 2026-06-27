package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.AdminSummaryDto;
import martin.game.model.Role;
import martin.game.model.User;
import martin.game.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 管理后台入口（占位实现）。完整的用户管理 UI 留待后续。
 *
 * <p>路由前缀 {@code /admin}，由 {@code SecurityConfig} 拦截，仅 {@code ROLE_ADMIN} 可访问。</p>
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    /**
     * 后台首页：欢迎词 + 3 个统计数字 + 预留用户列表容器。
     */
    @GetMapping({"", "/"})
    public String dashboard(Authentication authentication, Model model) {
        String me = authentication.getName();
        User meUser = userService.findByUsername(me);

        // 统计
        long total = userService.findByRole(Role.PLAYER).size()
                + userService.findByRole(Role.VIP).size()
                + userService.findByRole(Role.SVIP).size()
                + userService.findByRole(Role.ADMIN).size();
        long adminCount = userService.countByRole(Role.ADMIN);
        long vipCount = userService.countByRole(Role.VIP) + userService.countByRole(Role.SVIP);

        AdminSummaryDto summary = new AdminSummaryDto(total, adminCount, vipCount);

        // 简单预取：所有用户（最多 200，足够个人项目），实际管理页再加分页
        List<User> allUsers = userService.findByRole(Role.PLAYER);
        allUsers.addAll(userService.findByRole(Role.VIP));
        allUsers.addAll(userService.findByRole(Role.SVIP));
        allUsers.addAll(userService.findByRole(Role.ADMIN));

        model.addAttribute("me", meUser);
        model.addAttribute("meRole", meUser.getEffectiveRole().name());
        model.addAttribute("summary", summary);
        model.addAttribute("users", allUsers);

        return "admin";
    }
}
