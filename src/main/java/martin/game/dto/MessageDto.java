package martin.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条私信。前端用 sender 是否等于当前用户来判断左右气泡。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageDto {
    private Long id;
    private String sender;
    private String receiver;
    private String content;
    private String createTime;
}
