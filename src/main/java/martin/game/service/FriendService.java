package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.dto.FriendDto;
import martin.game.dto.FriendNotification;
import martin.game.dto.FriendRequestDto;
import martin.game.dto.FriendSearchDto;
import martin.game.model.FriendRequest;
import martin.game.model.FriendRequestStatus;
import martin.game.model.User;
import martin.game.repository.FriendRequestRepository;
import martin.game.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 好友服务：昵称搜索、发起/通过/拒绝好友申请、好友列表，并通过 STOMP 给在线用户实时推送。
 *
 * <p>关系以 {@link FriendRequest} 单表表达：PENDING=申请中，ACCEPTED=好友（双向匹配）。
 * 实时推送走 {@code convertAndSendToUser(user, "/queue/friend", ...)}；申请已落库，
 * 故离线 / 跨实例也不丢，对方下次进大厅可见。
 */
@Service
@RequiredArgsConstructor
public class FriendService {

    public static final String USER_QUEUE = "/queue/friend";

    // 搜索结果中的关系标记
    private static final String REL_NONE = "NONE";
    private static final String REL_FRIEND = "FRIEND";
    private static final String REL_SENT = "REQUEST_SENT";
    private static final String REL_RECEIVED = "REQUEST_RECEIVED";

    private static final int SEARCH_LIMIT = 20;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceTracker presenceTracker;

    private static final Logger logger = LogManager.getLogger(FriendService.class);

    /** 按昵称模糊搜索用户，并标注与我的关系。排除自己。 */
    public List<FriendSearchDto> search(String me, String nicknameQuery) {
        if (nicknameQuery == null || nicknameQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 一次性取出与我相关的全部关系，避免逐个查库
        Map<String, String> relationOf = new HashMap<>();
        Map<String, Long> receivedRequestId = new HashMap<>();
        for (FriendRequest f : friendRequestRepository.findAllInvolving(me)) {
            boolean iAmRequester = f.getRequester().equals(me);
            String other = iAmRequester ? f.getAddressee() : f.getRequester();
            if (f.getStatus() == FriendRequestStatus.ACCEPTED) {
                relationOf.put(other, REL_FRIEND);
            } else if (iAmRequester) {
                relationOf.put(other, REL_SENT);
            } else {
                relationOf.put(other, REL_RECEIVED);
                receivedRequestId.put(other, f.getId());
            }
        }

        return userRepository.findByNicknameContainingIgnoreCase(nicknameQuery.trim()).stream()
                .filter(u -> !u.getUsername().equals(me))
                .limit(SEARCH_LIMIT)
                .map(u -> {
                    String rel = relationOf.getOrDefault(u.getUsername(), REL_NONE);
                    return new FriendSearchDto(u.getUsername(), u.getNickname(), avatarUrl(u),
                            rel, receivedRequestId.get(u.getUsername()));
                })
                .collect(Collectors.toList());
    }

    /** 发起好友申请。若对方此前已申请过我，则直接互相通过。 */
    public void sendRequest(String me, String addressee) {
        if (addressee == null || addressee.equals(me)) {
            throw new IllegalArgumentException("不能添加自己为好友");
        }
        User target = userRepository.findByUsername(addressee)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        User self = userRepository.findByUsername(me)
                .orElseThrow(() -> new IllegalArgumentException("登录态异常"));

        // 我 -> 对方 方向已有记录
        Optional<FriendRequest> forward = friendRequestRepository.findByRequesterAndAddressee(me, addressee);
        if (forward.isPresent()) {
            if (forward.get().getStatus() == FriendRequestStatus.ACCEPTED) {
                throw new IllegalArgumentException("你们已经是好友");
            }
            throw new IllegalArgumentException("已发送过申请，请等待对方处理");
        }

        // 对方 -> 我 方向已有记录
        Optional<FriendRequest> reverse = friendRequestRepository.findByRequesterAndAddressee(addressee, me);
        if (reverse.isPresent()) {
            FriendRequest r = reverse.get();
            if (r.getStatus() == FriendRequestStatus.ACCEPTED) {
                throw new IllegalArgumentException("你们已经是好友");
            }
            // 对方早已申请我 -> 直接通过，互为好友
            r.setStatus(FriendRequestStatus.ACCEPTED);
            friendRequestRepository.save(r);
            push(addressee, new FriendNotification("ACCEPTED", me, self.getNickname(), avatarUrl(self),
                    null, self.getNickname() + " 通过了你的好友申请"));
            return;
        }

        FriendRequest req = friendRequestRepository.save(
                new FriendRequest(me, addressee, FriendRequestStatus.PENDING));
        push(addressee, new FriendNotification("NEW_REQUEST", me, self.getNickname(), avatarUrl(self),
                req.getId(), self.getNickname() + " 请求添加你为好友"));
    }

    /** 通过一条好友申请（只有接收人本人可操作）。 */
    public void accept(String me, Long requestId) {
        FriendRequest req = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
        if (!req.getAddressee().equals(me)) {
            throw new IllegalArgumentException("无权处理该申请");
        }
        if (req.getStatus() != FriendRequestStatus.PENDING) {
            throw new IllegalArgumentException("该申请已处理");
        }
        req.setStatus(FriendRequestStatus.ACCEPTED);
        friendRequestRepository.save(req);

        User self = userRepository.findByUsername(me).orElse(null);
        String nick = self != null ? self.getNickname() : me;
        String icon = self != null ? avatarUrl(self) : null;
        push(req.getRequester(), new FriendNotification("ACCEPTED", me, nick, icon,
                null, nick + " 通过了你的好友申请"));
    }

    /** 拒绝一条好友申请（删除记录，允许日后重新申请）。 */
    public void reject(String me, Long requestId) {
        FriendRequest req = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
        if (!req.getAddressee().equals(me)) {
            throw new IllegalArgumentException("无权处理该申请");
        }
        friendRequestRepository.delete(req);
    }

    /** 删除好友（删除已通过的双向记录）。 */
    public void removeFriend(String me, String friend) {
        friendRequestRepository.findByRequesterAndAddressee(me, friend)
                .ifPresent(friendRequestRepository::delete);
        friendRequestRepository.findByRequesterAndAddressee(friend, me)
                .ifPresent(friendRequestRepository::delete);
    }

    /** 我收到的待处理申请。 */
    public List<FriendRequestDto> incomingRequests(String me) {
        List<FriendRequest> pending = friendRequestRepository.findByAddresseeAndStatus(me, FriendRequestStatus.PENDING);
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, User> users = loadUsers(pending.stream().map(FriendRequest::getRequester).collect(Collectors.toList()));
        return pending.stream().map(f -> {
            User u = users.get(f.getRequester());
            return new FriendRequestDto(f.getId(), f.getRequester(),
                    u != null ? u.getNickname() : f.getRequester(),
                    u != null ? avatarUrl(u) : null,
                    f.getCreateTime() != null ? f.getCreateTime().format(TS) : "");
        }).collect(Collectors.toList());
    }

    /** 我的好友列表。 */
    public List<FriendDto> listFriends(String me) {
        List<FriendRequest> accepted = friendRequestRepository.findAcceptedInvolving(me);
        if (accepted.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> others = accepted.stream()
                .map(f -> f.getRequester().equals(me) ? f.getAddressee() : f.getRequester())
                .collect(Collectors.toList());
        Map<String, User> users = loadUsers(others);
        Set<String> online = presenceTracker.onlineAmong(others);
        return others.stream().distinct().map(name -> {
            User u = users.get(name);
            return new FriendDto(name, u != null ? u.getNickname() : name,
                    u != null ? avatarUrl(u) : null, online.contains(name));
        }).collect(Collectors.toList());
    }

    // ---------------- 内部工具 ----------------

    private Map<String, User> loadUsers(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(User::getUsername, u -> u, (a, b) -> a));
    }

    private void push(String username, FriendNotification notification) {
        try {
            messagingTemplate.convertAndSendToUser(username, USER_QUEUE, notification);
        } catch (Exception e) {
            // 推送失败不影响主流程：申请已落库，对方下次进大厅仍可见
            logger.warn("好友通知推送失败 user={}, err={}", username, e.getMessage());
        }
    }

    private String avatarUrl(User u) {
        if (u == null || u.getIconUrl() == null) {
            return "/icon/default-avatar.jpg";
        }
        return "/avatar/" + u.getIconUrl();
    }
}
