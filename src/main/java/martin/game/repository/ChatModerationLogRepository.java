package martin.game.repository;

import martin.game.model.ChatModerationLog;
import martin.game.model.ModerationAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatModerationLogRepository extends JpaRepository<ChatModerationLog, Long> {

    List<ChatModerationLog> findAllByOrderByCreateTimeDesc(Pageable pageable);

    List<ChatModerationLog> findByUsernameOrderByCreateTimeDesc(String username, Pageable pageable);

    List<ChatModerationLog> findByActionOrderByCreateTimeDesc(ModerationAction action, Pageable pageable);

    List<ChatModerationLog> findByUsernameAndActionOrderByCreateTimeDesc(
            String username, ModerationAction action, Pageable pageable);
}
