package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 昵称搜索结果项。relation 决定前端展示哪种按钮：
 * NONE(加好友) / REQUEST_SENT(已申请) / REQUEST_RECEIVED(同意) / FRIEND(已是好友)。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendSearchDto {
    private String username;
    private String nickname;
    private String iconUrl;
    private String relation;
    /** 当 relation=REQUEST_RECEIVED 时带上申请 id，前端可直接同意；否则为 null */
    private Long requestId;
    /** 有效角色名（PLAYER/VIP/SVIP/ADMIN） */
    private String role;
}
