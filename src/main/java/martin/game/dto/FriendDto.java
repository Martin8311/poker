package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友列表项。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendDto {
    private String username;
    private String nickname;
    private String iconUrl;
    private boolean online;
}
