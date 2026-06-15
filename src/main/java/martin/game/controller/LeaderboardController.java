package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.LeaderboardEntry;
import martin.game.service.LeaderboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排行榜接口。
 */
@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /** 积分榜前 N 名（默认 10） */
    @GetMapping("/top")
    public List<LeaderboardEntry> top(@RequestParam(defaultValue = "10") int n) {
        return leaderboardService.getTopN(n);
    }

    /** 当前登录用户的排名与积分 */
    @GetMapping("/me")
    public LeaderboardEntry me(Authentication authentication) {
        return leaderboardService.getUserEntry(authentication.getName());
    }

    /** 从数据库手动重建排行榜（数据修复 / 调试用） */
    @PostMapping("/rebuild")
    public String rebuild() {
        long count = leaderboardService.rebuildFromDb();
        return "rebuilt " + count + " users";
    }
}
