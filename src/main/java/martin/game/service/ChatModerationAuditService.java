package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.model.ChatModerationLog;
import martin.game.model.ModerationAction;
import martin.game.repository.ChatModerationLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatModerationAuditService {

    private static final int MAX_CONTENT_LENGTH = 500;

    private final ChatModerationLogRepository logRepository;

    public void record(String roomId,
                       String username,
                       String nickname,
                       String originalContent,
                       ContentModerationService.ModerationResult result) {
        if (result.getAction() == ModerationAction.PASS) {
            return;
        }

        ChatModerationLog log = new ChatModerationLog();
        log.setRoomId(trimToLength(roomId, 64));
        log.setUsername(trimToLength(username, 45));
        log.setNickname(trimToLength(nickname, 45));
        log.setOriginalContent(trimToLength(Optional.ofNullable(originalContent).orElse(""), MAX_CONTENT_LENGTH));
        log.setSanitizedContent(trimToLength(result.getSanitizedText(), MAX_CONTENT_LENGTH));
        log.setAction(result.getAction());
        log.setReason(trimToLength(result.getReason(), 200));
        logRepository.save(log);
    }

    public List<ChatModerationLog> latest(String username, ModerationAction action, int size) {
        int limit = size <= 0 || size > 200 ? 100 : size;
        PageRequest page = PageRequest.of(0, limit);
        boolean hasUsername = username != null && !username.isBlank();
        if (hasUsername && action != null) {
            return logRepository.findByUsernameAndActionOrderByCreateTimeDesc(username.trim(), action, page);
        }
        if (hasUsername) {
            return logRepository.findByUsernameOrderByCreateTimeDesc(username.trim(), page);
        }
        if (action != null) {
            return logRepository.findByActionOrderByCreateTimeDesc(action, page);
        }
        return logRepository.findAllByOrderByCreateTimeDesc(page);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
