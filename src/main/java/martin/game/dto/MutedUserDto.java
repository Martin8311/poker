package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MutedUserDto {
    private String username;
    private String nickname;
    private long ttlSeconds;
}
