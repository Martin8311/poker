package martin.game.repository;

import martin.game.model.PrivateMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    /** 两人之间的会话（双向），按 id 倒序取最近若干条（服务层再反转为正序展示） */
    @Query("select m from PrivateMessage m " +
            "where (m.sender = :a and m.receiver = :b) or (m.sender = :b and m.receiver = :a) " +
            "order by m.id desc")
    List<PrivateMessage> findConversation(@Param("a") String a, @Param("b") String b, Pageable pageable);

    /** 把「friend 发给 me」且未读的消息标记为已读 */
    @Modifying
    @Query("update PrivateMessage m set m.read = true " +
            "where m.sender = :friend and m.receiver = :me and m.read = false")
    int markRead(@Param("friend") String friend, @Param("me") String me);

    /** 我的未读消息总数 */
    long countByReceiverAndReadFalse(String receiver);

    /** 我的未读消息按发送人分组计数：[sender, count] */
    @Query("select m.sender, count(m) from PrivateMessage m " +
            "where m.receiver = :me and m.read = false group by m.sender")
    List<Object[]> unreadBySender(@Param("me") String me);
}
