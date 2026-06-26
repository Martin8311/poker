package martin.game.controller;

import lombok.RequiredArgsConstructor;
import martin.game.dto.MessageDto;
import martin.game.dto.UnreadSummary;
import martin.game.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友私信接口。全部需登录，当前用户取自认证身份。
 * 路径用 query 参数区分会话对象，避免 /{friend} 与 /unread 等字面量冲突。
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 发送私信 */
    @PostMapping("/send")
    public MessageDto send(@RequestParam("to") String to,
                           @RequestParam("content") String content,
                           Authentication auth) {
        return messageService.send(auth.getName(), to, content);
    }

    /** 与某好友的会话历史（最近 size 条，正序），打开即标记对方消息已读 */
    @GetMapping("/history")
    public List<MessageDto> history(@RequestParam("friend") String friend,
                                    @RequestParam(value = "size", defaultValue = "50") int size,
                                    Authentication auth) {
        return messageService.history(auth.getName(), friend, size);
    }

    /** 未读汇总：总数 + 每个好友未读数 */
    @GetMapping("/unread")
    public UnreadSummary unread(Authentication auth) {
        return messageService.unreadSummary(auth.getName());
    }

    /** 把某好友发来的消息标记为已读 */
    @PostMapping("/read")
    public ResponseEntity<?> read(@RequestParam("friend") String friend, Authentication auth) {
        messageService.markRead(auth.getName(), friend);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> onBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
