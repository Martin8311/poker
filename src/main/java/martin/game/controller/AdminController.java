package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.AdminSummaryDto;
import martin.game.model.ModerationAction;
import martin.game.model.Role;
import martin.game.model.User;
import martin.game.service.ChatModerationAuditService;
import martin.game.service.ContentModerationService;
import martin.game.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final ContentModerationService contentModerationService;
    private final ChatModerationAuditService chatModerationAuditService;

    @GetMapping({"", "/"})
    public String dashboard(Authentication authentication,
                            @RequestParam(value = "logUsername", required = false) String logUsername,
                            @RequestParam(value = "logAction", required = false) String logAction,
                            @RequestParam(value = "userKeyword", required = false) String userKeyword,
                            Model model) {
        String me = authentication.getName();
        User meUser = userService.findByUsername(me);

        long total = userService.findByRole(Role.PLAYER).size()
                + userService.findByRole(Role.VIP).size()
                + userService.findByRole(Role.SVIP).size()
                + userService.findByRole(Role.ADMIN).size();
        long adminCount = userService.countByRole(Role.ADMIN);
        long vipCount = userService.countByRole(Role.VIP) + userService.countByRole(Role.SVIP);

        AdminSummaryDto summary = new AdminSummaryDto(total, adminCount, vipCount);

        List<User> allUsers = userService.findAdminUsers(userKeyword);

        model.addAttribute("me", meUser);
        model.addAttribute("meRole", meUser.getEffectiveRole().name());
        model.addAttribute("summary", summary);
        model.addAttribute("users", allUsers);
        model.addAttribute("userKeyword", userKeyword);
        model.addAttribute("maskWords", contentModerationService.listWords(ContentModerationService.WordType.MASK));
        model.addAttribute("rejectWords", contentModerationService.listWords(ContentModerationService.WordType.REJECT));
        model.addAttribute("mutedUsers", contentModerationService.listMutedUsers());
        model.addAttribute("moderationActions", ModerationAction.values());
        model.addAttribute("logUsername", logUsername);
        model.addAttribute("logAction", logAction);
        model.addAttribute("moderationLogs",
                chatModerationAuditService.latest(logUsername, parseAction(logAction), 100));

        return "admin";
    }

    @PostMapping("/moderation/words")
    public String addModerationWord(@RequestParam("type") String type,
                                    @RequestParam("word") String word,
                                    RedirectAttributes redirectAttributes) {
        try {
            contentModerationService.addWord(ContentModerationService.WordType.from(type), word);
            redirectAttributes.addFlashAttribute("moderationMessage", "敏感词已添加");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("moderationError", e.getMessage());
        }
        return "redirect:/admin#moderation";
    }

    @PostMapping("/moderation/words/delete")
    public String deleteModerationWord(@RequestParam("type") String type,
                                       @RequestParam("word") String word,
                                       RedirectAttributes redirectAttributes) {
        contentModerationService.deleteWord(ContentModerationService.WordType.from(type), word);
        redirectAttributes.addFlashAttribute("moderationMessage", "敏感词已删除");
        return "redirect:/admin#moderation";
    }

    @PostMapping("/moderation/mutes/release")
    public String releaseMute(@RequestParam("username") String username,
                              RedirectAttributes redirectAttributes) {
        contentModerationService.unmute(username);
        redirectAttributes.addFlashAttribute("moderationMessage", "已解除禁言");
        return "redirect:/admin#moderation-audit";
    }

    @PostMapping("/users/ban")
    public String banUser(Authentication authentication,
                          @RequestParam("username") String username,
                          @RequestParam(value = "reason", required = false) String reason,
                          @RequestParam(value = "durationAmount", required = false) Integer durationAmount,
                          @RequestParam(value = "durationUnit", required = false) String durationUnit,
                          @RequestParam(value = "userKeyword", required = false) String userKeyword,
                          RedirectAttributes redirectAttributes) {
        try {
            userService.banUser(authentication.getName(), username, reason, durationAmount, durationUnit);
            redirectAttributes.addFlashAttribute("userMessage", "账号已封禁");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("userError", e.getMessage());
        }
        return redirectToUsers(userKeyword);
    }

    @PostMapping("/users/unban")
    public String unbanUser(@RequestParam("username") String username,
                            @RequestParam(value = "userKeyword", required = false) String userKeyword,
                            RedirectAttributes redirectAttributes) {
        try {
            userService.unbanUser(username);
            redirectAttributes.addFlashAttribute("userMessage", "账号已解封");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("userError", e.getMessage());
        }
        return redirectToUsers(userKeyword);
    }

    private String redirectToUsers(String userKeyword) {
        if (userKeyword == null || userKeyword.isBlank()) {
            return "redirect:/admin#users";
        }
        return "redirect:/admin?userKeyword="
                + UriUtils.encodeQueryParam(userKeyword.trim(), StandardCharsets.UTF_8)
                + "#users";
    }

    private ModerationAction parseAction(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return null;
        }
        try {
            return ModerationAction.valueOf(rawAction);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
