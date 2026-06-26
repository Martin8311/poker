package martin.game.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友申请 / 好友关系。
 *
 * <p>一条记录表达「requester -> addressee」的关系：
 * <ul>
 *   <li>{@code PENDING}  申请中（addressee 尚未处理）</li>
 *   <li>{@code ACCEPTED} 已通过 —— 即代表两人互为好友（无需再存反向记录，查询时双向匹配）</li>
 * </ul>
 * 拒绝时直接删除记录。(requester, addressee) 唯一，避免重复申请。
 */
@Entity
@Table(name = "friend_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_pair", columnNames = {"requester", "addressee"}))
@Data
@NoArgsConstructor
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发起人 username */
    @Column(nullable = false, length = 45)
    private String requester;

    /** 接收人 username */
    @Column(nullable = false, length = 45)
    private String addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendRequestStatus status;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public FriendRequest(String requester, String addressee, FriendRequestStatus status) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = status;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
