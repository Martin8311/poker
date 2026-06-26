package martin.game.model;

/**
 * 好友申请状态。
 * <ul>
 *   <li>{@code PENDING}  - 已发送，等待对方处理</li>
 *   <li>{@code ACCEPTED} - 已通过（即代表两人是好友关系）</li>
 * </ul>
 * 拒绝的申请直接删除记录（允许日后重新申请），故不设 REJECTED 持久态。
 */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED
}
