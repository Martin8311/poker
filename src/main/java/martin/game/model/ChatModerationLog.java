package martin.game.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_moderation_log",
        indexes = {
                @Index(name = "idx_chat_mod_user_time", columnList = "username, create_time"),
                @Index(name = "idx_chat_mod_action_time", columnList = "action, create_time"),
                @Index(name = "idx_chat_mod_room_time", columnList = "room_id, create_time")
        }
)
@Data
public class ChatModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 64)
    private String roomId;

    @Column(nullable = false, length = 45)
    private String username;

    @Column(length = 45)
    private String nickname;

    @Column(name = "original_content", nullable = false, length = 500)
    private String originalContent;

    @Column(name = "sanitized_content", length = 500)
    private String sanitizedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationAction action;

    @Column(length = 200)
    private String reason;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
