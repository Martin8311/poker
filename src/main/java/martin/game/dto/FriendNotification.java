package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通过 STOMP 推送给指定用户的好友事件载荷（订阅 /user/queue/friend）。
 *
 * <p>type:
 * <ul>
 *   <li>NEW_REQUEST 收到新的好友申请</li>
 *   <li>ACCEPTED    我发出的申请被对方通过</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendNotification {
    private String type;
    private String fromUsername;
    private String fromNickname;
    private String fromIcon;
    /** 仅 NEW_REQUEST 时有值，便于前端直接同意 */
    private Long requestId;
    private String message;
}
