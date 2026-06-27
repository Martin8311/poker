package martin.game.service;

import martin.game.dto.MessageDto;
import martin.game.model.FriendRequest;
import martin.game.model.FriendRequestStatus;
import martin.game.model.PrivateMessage;
import martin.game.model.User;
import martin.game.repository.FriendRequestRepository;
import martin.game.repository.PrivateMessageRepository;
import martin.game.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendAndMessageServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private PrivateMessageRepository messageRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PresenceTracker presenceTracker;

    @Nested
    @DisplayName("FriendService")
    class FriendServiceCases {

        @Test
        @DisplayName("对方已经申请我时，我再申请会直接互相通过")
        void reverseRequestAutoAccepts() {
            FriendService service = new FriendService(
                    userRepository, friendRequestRepository, messagingTemplate, presenceTracker);
            FriendRequest reverse = new FriendRequest("bob", "alice", FriendRequestStatus.PENDING);
            User alice = user("alice", "Alice");
            User bob = user("bob", "Bob");

            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(friendRequestRepository.findByRequesterAndAddressee("alice", "bob")).thenReturn(Optional.empty());
            when(friendRequestRepository.findByRequesterAndAddressee("bob", "alice")).thenReturn(Optional.of(reverse));

            service.sendRequest("alice", "bob");

            assertThat(reverse.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
            verify(friendRequestRepository).save(reverse);
            verify(messagingTemplate).convertAndSendToUser(
                    org.mockito.Mockito.eq("bob"),
                    org.mockito.Mockito.eq(FriendService.USER_QUEUE),
                    any());
        }

        @Test
        @DisplayName("不能给自己发送好友申请")
        void cannotFriendSelf() {
            FriendService service = new FriendService(
                    userRepository, friendRequestRepository, messagingTemplate, presenceTracker);

            assertThatThrownBy(() -> service.sendRequest("alice", "alice"))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(friendRequestRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("MessageService")
    class MessageServiceCases {

        @Test
        @DisplayName("非好友不能发送私信")
        void nonFriendsCannotSendDm() {
            MessageService service = new MessageService(
                    messageRepository, friendRequestRepository, messagingTemplate);
            when(friendRequestRepository.areFriends("alice", "bob")).thenReturn(false);

            assertThatThrownBy(() -> service.send("alice", "bob", "hello"))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("好友私信会落库并实时推送给接收人")
        void friendsCanSendDm() {
            MessageService service = new MessageService(
                    messageRepository, friendRequestRepository, messagingTemplate);
            when(friendRequestRepository.areFriends("alice", "bob")).thenReturn(true);
            when(messageRepository.save(any(PrivateMessage.class))).thenAnswer(inv -> {
                PrivateMessage message = inv.getArgument(0);
                message.setId(100L);
                message.prePersist();
                return message;
            });

            MessageDto dto = service.send("alice", "bob", "hello");

            assertThat(dto.getSender()).isEqualTo("alice");
            assertThat(dto.getReceiver()).isEqualTo("bob");
            assertThat(dto.getContent()).isEqualTo("hello");

            ArgumentCaptor<PrivateMessage> saved = ArgumentCaptor.forClass(PrivateMessage.class);
            verify(messageRepository).save(saved.capture());
            assertThat(saved.getValue().getContent()).isEqualTo("hello");
            verify(messagingTemplate).convertAndSendToUser("bob", MessageService.DM_QUEUE, dto);
        }
    }

    private static User user(String username, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        return user;
    }
}
