package martin.game.service;

import martin.game.model.ChatModerationLog;
import martin.game.model.ModerationAction;
import martin.game.repository.ChatModerationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatModerationAuditServiceTest {

    @Mock
    private ChatModerationLogRepository logRepository;

    private ChatModerationAuditService service;

    @BeforeEach
    void setUp() {
        service = new ChatModerationAuditService(logRepository);
    }

    @Test
    @DisplayName("正常放行聊天不写入审计日志")
    void passDoesNotCreateAuditLog() {
        service.record("room1", "alice", "Alice", "hello",
                ContentModerationService.ModerationResult.pass("hello"));

        verify(logRepository, never()).save(org.mockito.ArgumentMatchers.any(ChatModerationLog.class));
    }

    @Test
    @DisplayName("脱敏聊天写入审计日志")
    void maskedMessageCreatesAuditLog() {
        String content = "x".repeat(520);

        service.record("room1", "alice", "Alice", content,
                ContentModerationService.ModerationResult.mask("safe"));

        ArgumentCaptor<ChatModerationLog> captor = ArgumentCaptor.forClass(ChatModerationLog.class);
        verify(logRepository).save(captor.capture());
        ChatModerationLog log = captor.getValue();
        assertThat(log.getRoomId()).isEqualTo("room1");
        assertThat(log.getUsername()).isEqualTo("alice");
        assertThat(log.getAction()).isEqualTo(ModerationAction.MASK);
        assertThat(log.getOriginalContent()).hasSize(500);
        assertThat(log.getSanitizedContent()).isEqualTo("safe");
    }
}
