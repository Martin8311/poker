package martin.game.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import martin.game.dto.UserInfo;
import martin.game.model.GameMessage;
import martin.game.model.Room;
import martin.game.model.User;
import martin.game.service.ContentModerationService;
import martin.game.service.RoomService;
import martin.game.service.UserService;
import martin.game.utils.SHA256Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final UserService userService;
    private final ContentModerationService contentModerationService;
    private final ObjectMapper objectMapper;

    private static final Logger logger = LogManager.getLogger(RoomController.class);

    @PostMapping("/room/create")
    public String createRoom(@RequestParam("roomDesc") String roomDesc,
                             @RequestParam(value = "roomPwd") String roomPwd,
                             RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(authentication.getName());

        ContentModerationService.ModerationResult moderation =
                contentModerationService.moderateRoomDescription(currentUser.getUsername(), roomDesc);
        if (!moderation.isPassed()) {
            redirectAttributes.addFlashAttribute("error", moderation.getReason());
            return "redirect:/hall";
        }

        Room room = roomService.createRoom(currentUser, moderation.getSanitizedText(), roomPwd);
        logger.info("{} created room {}", currentUser.getUsername(), room.getRoomId());
        return "redirect:/room/" + room.getRoomId();
    }

    @PostMapping("/room/join/{roomId}")
    public String joinRoom(@PathVariable String roomId, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(authentication.getName());
        boolean joined = roomService.joinRoom(roomId, currentUser);

        if (joined) {
            return "redirect:/room/" + roomId;
        }
        redirectAttributes.addFlashAttribute("error", "加入房间失败，房间可能已满或不存在");
        return "redirect:/hall";
    }

    @PostMapping("/room/verify-password")
    @ResponseBody
    public String verifyPassword(@RequestParam String roomId,
                                 @RequestParam String password) throws JsonProcessingException {
        Room room = roomService.getRoom(roomId);
        boolean isValid = room != null && SHA256Utils.sha256Encrypt(password).equals(room.getRoomPassword());
        Map<String, Boolean> response = new HashMap<>();
        response.put("valid", isValid);
        logger.info(isValid ? "room password verified" : "room password rejected");
        return objectMapper.writeValueAsString(response);
    }

    @GetMapping("/room/{roomId}")
    public String showRoom(@PathVariable String roomId, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(authentication.getName());
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            model.addAttribute("error", "房间不存在或已关闭");
            return "hall";
        }

        try {
            boolean isInRoom = room.getPlayers().stream()
                    .anyMatch(player -> player.getId().equals(currentUser.getId()));
            if (!isInRoom) {
                model.addAttribute("error", "你不在这个房间内");
                return "hall";
            }

            model.addAttribute("roomId", roomId);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("players", room.getPlayers());
            model.addAttribute("playersJson", objectMapper.writeValueAsString(room.getPlayers()));
            model.addAttribute("isCreator", room.getCreator().getId().equals(currentUser.getId()));
            model.addAttribute("gameStarted", room.isGameStarted());
            return "room";
        } catch (JsonProcessingException e) {
            logger.warn("serialize room players failed: {}", e.getMessage());
            return "hall";
        }
    }

    @PostMapping("/room/{roomId}/get_userinfo")
    @ResponseBody
    public UserInfo getUserInfoByUsername(@PathVariable String roomId, @RequestBody String username) {
        User user = userService.findByUsername(username);
        return new UserInfo(user.getNickname(), user.getScore(), user.getTotalGames(),
                user.getWinGames(), user.getIconUrl(), user.getEffectiveRole().name());
    }

    @PostMapping("/room/{roomId}/get_creator_name")
    @ResponseBody
    public String getCreatorName(@PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        return room.getCreator().getUsername();
    }

    @MessageMapping("/room/{roomId}/isReady")
    @SendTo("/topic/rooms.{roomId}")
    public GameMessage handleIsReady(@DestinationVariable String roomId, GameMessage message) {
        roomService.handleReadyEvent(roomId, message.isReady());
        return message;
    }

    @PostMapping("/room/{roomId}/get_room_status")
    @ResponseBody
    public String getRoomStatus(@PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room != null && room.isGameStarted()) {
            return "START";
        }
        return "WAIT";
    }
}
