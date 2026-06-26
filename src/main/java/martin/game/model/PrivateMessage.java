package martin.game.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友私信。一条记录表示 sender -> receiver 的一条消息；
 * 两人之间的会话 = 双向（sender/receiver 互换）的全部消息按时间排序。
 */
@Entity
@Table(name = "private_message", indexes = {
        @Index(name = "idx_pair_time", columnList = "sender,receiver,create_time"),
        @Index(name = "idx_unread", columnList = "receiver,is_read")
})
@Data
@NoArgsConstructor
public class PrivateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String sender;

    @Column(nullable = false, length = 45)
    private String receiver;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 接收方是否已读 */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    public PrivateMessage(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.read = false;
    }

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
