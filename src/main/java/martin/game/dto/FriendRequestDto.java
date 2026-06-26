package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 我收到的一条待处理好友申请（含发起人资料，供列表展示）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendRequestDto {
    private Long id;
    private String requesterUsername;
    private String requesterNickname;
    private String requesterIcon;
    private String createTime;
}
