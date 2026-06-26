package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.FriendDto;
import martin.game.dto.FriendRequestDto;
import martin.game.dto.FriendSearchDto;
import martin.game.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友接口。所有路径都需登录（SecurityConfig 的 anyRequest().authenticated() 覆盖），
 * 当前用户一律取自认证身份，不信任前端传入的身份。
 */
@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /** 按昵称模糊搜索 */
    @GetMapping("/search")
    public List<FriendSearchDto> search(@RequestParam("nickname") String nickname, Authentication auth) {
        return friendService.search(auth.getName(), nickname);
    }

    /** 发起好友申请（局内 / 搜索结果共用，username = 对方用户名） */
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestParam("username") String username, Authentication auth) {
        friendService.sendRequest(auth.getName(), username);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 我收到的待处理申请 */
    @GetMapping("/requests")
    public List<FriendRequestDto> requests(Authentication auth) {
        return friendService.incomingRequests(auth.getName());
    }

    /** 通过申请 */
    @PostMapping("/request/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, Authentication auth) {
        friendService.accept(auth.getName(), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 拒绝申请 */
    @PostMapping("/request/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        friendService.reject(auth.getName(), id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 我的好友列表 */
    @GetMapping
    public List<FriendDto> friends(Authentication auth) {
        return friendService.listFriends(auth.getName());
    }

    /** 删除好友 */
    @PostMapping("/remove")
    public ResponseEntity<?> remove(@RequestParam("username") String username, Authentication auth) {
        friendService.removeFriend(auth.getName(), username);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 业务校验失败统一返回 400 + 错误信息 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> onBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
