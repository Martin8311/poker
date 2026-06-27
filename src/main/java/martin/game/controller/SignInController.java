package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.SignInResultDto;
import martin.game.dto.SignInStatusDto;
import martin.game.service.SignInService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * 签到接口。
 *
 * <pre>
 * GET  /signin/today            → 今日是否已签（登录后弹窗判断用）
 * GET  /signin/status?y&m       → 月度日历视图
 * GET  /signin/summary          → 顶部信息条
 * POST /signin/do               → 执行签到
 * </pre>
 */
@RestController
@RequestMapping("/signin")
@RequiredArgsConstructor
public class SignInController {

    private final SignInService signInService;

    @GetMapping("/today")
    public Map<String, Object> today(Authentication authentication) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("signed", signInService.isSignedToday(authentication.getName()));
        resp.put("date", LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());
        return resp;
    }

    @GetMapping("/status")
    public SignInStatusDto status(Authentication authentication,
                                  @RequestParam(value = "year", required = false) Integer year,
                                  @RequestParam(value = "month", required = false) Integer month) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        return signInService.getMonthStatus(authentication.getName(), y, m);
    }

    @GetMapping("/summary")
    public SignInStatusDto summary(Authentication authentication) {
        return signInService.getSummary(authentication.getName());
    }

    @PostMapping("/do")
    public SignInResultDto doSignIn(Authentication authentication) {
        return signInService.doSignIn(authentication.getName());
    }
}
