package martin.game.repository;

import martin.game.model.FriendRequest;
import martin.game.model.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /** 某一方向上是否已有记录（申请中或已是好友） */
    Optional<FriendRequest> findByRequesterAndAddressee(String requester, String addressee);

    /** 我收到的、待处理的好友申请 */
    List<FriendRequest> findByAddresseeAndStatus(String addressee, FriendRequestStatus status);

    /** 与某用户相关的全部记录（双向），用于一次性算出搜索结果中的关系状态 */
    @Query("select f from FriendRequest f where f.requester = :u or f.addressee = :u")
    List<FriendRequest> findAllInvolving(@Param("u") String username);

    /** 我的好友（已通过的双向记录） */
    @Query("select f from FriendRequest f where f.status = martin.game.model.FriendRequestStatus.ACCEPTED " +
            "and (f.requester = :u or f.addressee = :u)")
    List<FriendRequest> findAcceptedInvolving(@Param("u") String username);

    /** a、b 是否已是好友（双向已通过） */
    @Query("select count(f) > 0 from FriendRequest f where f.status = martin.game.model.FriendRequestStatus.ACCEPTED " +
            "and ((f.requester = :a and f.addressee = :b) or (f.requester = :b and f.addressee = :a))")
    boolean areFriends(@Param("a") String a, @Param("b") String b);
}
