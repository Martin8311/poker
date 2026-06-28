package martin.game.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import martin.game.dto.MessageDto;
import martin.game.dto.UnreadSummary;
import martin.game.model.PrivateMessage;
import martin.game.repository.FriendRequestRepository;
import martin.game.repository.PrivateMessageRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String DM_QUEUE = "/queue/dm";

    private static final int DEFAULT_HISTORY = 50;
    private static final int MAX_HISTORY = 200;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final PrivateMessageRepository messageRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ContentModerationService contentModerationService;

    private static final Logger logger = LogManager.getLogger(MessageService.class);

    @Transactional
    public MessageDto send(String me, String to, String content) {
        if (to == null || to.equals(me)) {
            throw new IllegalArgumentException("不能给自己发私信");
        }
        if (!friendRequestRepository.areFriends(me, to)) {
            throw new IllegalArgumentException("仅好友之间可以私信");
        }

        String text = content == null ? "" : content.trim();
        String conversationId = me.compareTo(to) <= 0 ? me + ":" + to : to + ":" + me;
        ContentModerationService.ModerationResult moderation =
                contentModerationService.moderatePrivateMessage(conversationId, me, text);
        if (!moderation.isPassed()) {
            throw new IllegalArgumentException(moderation.getReason());
        }

        MessageDto dto = toDto(messageRepository.save(
                new PrivateMessage(me, to, moderation.getSanitizedText())));
        push(to, dto);
        return dto;
    }

    @Transactional
    public List<MessageDto> history(String me, String friend, int size) {
        if (!friendRequestRepository.areFriends(me, friend)) {
            throw new IllegalArgumentException("仅好友之间可以私信");
        }
        int n = (size <= 0 || size > MAX_HISTORY) ? DEFAULT_HISTORY : size;
        List<PrivateMessage> latest = messageRepository.findConversation(me, friend, PageRequest.of(0, n));
        Collections.reverse(latest);
        messageRepository.markRead(friend, me);
        return latest.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void markRead(String me, String friend) {
        messageRepository.markRead(friend, me);
    }

    public UnreadSummary unreadSummary(String me) {
        long total = messageRepository.countByReceiverAndReadFalse(me);
        Map<String, Long> byFriend = new HashMap<>();
        for (Object[] row : messageRepository.unreadBySender(me)) {
            byFriend.put((String) row[0], ((Number) row[1]).longValue());
        }
        return new UnreadSummary(total, byFriend);
    }

    private void push(String to, MessageDto dto) {
        try {
            messagingTemplate.convertAndSendToUser(to, DM_QUEUE, dto);
        } catch (Exception e) {
            logger.warn("私信推送失败: to={}, err={}", to, e.getMessage());
        }
    }

    private MessageDto toDto(PrivateMessage m) {
        return new MessageDto(m.getId(), m.getSender(), m.getReceiver(), m.getContent(),
                m.getCreateTime() != null ? m.getCreateTime().format(TS) : "");
    }
}
