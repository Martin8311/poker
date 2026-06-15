package martin.game.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import martin.game.dto.UserInfo;
import martin.game.model.GameMessage;
import martin.game.model.Room;
import martin.game.model.User;
import martin.game.service.RoomService;
import martin.game.service.UserService;
import martin.game.utils.SHA256Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Controller
public class RoomController {
    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper; // 注入Jackson的JSON处理工具

    private static final Logger logger = LogManager.getLogger(RoomController.class);

    /**
     * 创建新房间
     */
    @PostMapping("/room/create")
    public String createRoom(@RequestParam("roomDesc") String roomDesc,
                             @RequestParam(value = "roomPwd") String roomPwd,
                             RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(authentication.getName());
        logger.info(roomPwd);
        // 创建房间
        Room room = roomService.createRoom(currentUser, roomDesc, roomPwd);

        // 重定向到房间页面
        return "redirect:/room/" + room.getRoomId();
    }

    /**
     * 加入指定房间
     */
    @PostMapping("/room/join/{roomId}")
    public String joinRoom(@PathVariable String roomId, RedirectAttributes redirectAttributes) {
        // 获取当前登录用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(authentication.getName());

        // 尝试加入房间
        boolean joined = roomService.joinRoom(roomId, currentUser);

        if (joined) {
            // 加入成功，跳转到房间页面
            return "redirect:/room/" + roomId;
        } else {
            // 加入失败，返回大厅并提示错误
            redirectAttributes.addFlashAttribute("error", "加入房间失败，房间可能已满或不存在");
            return "redirect:/hall";
        }
    }

    /**
     * 房间密码校验
     */
    @PostMapping("/room/verify-password")
    @ResponseBody
    public String verifyPassword(@RequestParam String roomId, @RequestParam String password) throws JsonProcessingException {
        boolean isValid = SHA256Utils.sha256Encrypt(password).equals(roomService.getRoom(roomId).getRoomPassword());
        Map<String, Boolean> response = new HashMap<>();
        response.put("valid", isValid); // 核心：添加 valid 字段
        logger.info(isValid ? "校验通过" : "校验不通过");
        return objectMapper.writeValueAsString(response);
    }


    /**
     * 显示房间页面
     */
    @GetMapping("/room/{roomId}")
    public String showRoom(@PathVariable String roomId, Model model) {
        // 获取当前登录用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(authentication.getName());

        // 获取房间信息
        Room room = roomService.getRoomWithLock(roomId);
        if (room == null) {
            model.addAttribute("error", "房间不存在或已关闭");
            return "hall";
        }

        try {
            // 检查当前用户是否在房间内
            boolean isInRoom = room.getPlayers().stream()
                    .anyMatch(player -> player.getId().equals(currentUser.getId()));

            if (!isInRoom) {
                model.addAttribute("error", "您不在这个房间内");
                return "hall";
            }

            // 3. 新增：将玩家列表转换为JSON字符串
            String playersJson = objectMapper.writeValueAsString(room.getPlayers());

            // 添加房间信息到模型
            model.addAttribute("roomId", roomId);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("players", room.getPlayers());
            model.addAttribute("playersJson", playersJson);
            model.addAttribute("isCreator", room.getCreator().getId().equals(currentUser.getId()));
            model.addAttribute("gameStarted", room.isGameStarted());

            return "room";
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "hall";
        } finally {
            // 释放房间锁
            roomService.releaseRoomLock(roomId);
        }
    }

    @PostMapping("/room/{roomId}/get_userinfo")
    @ResponseBody
    public UserInfo getUserInfoByUsername(@PathVariable String roomId, @RequestBody String username){
        User user = userService.findByUsername(username);
        UserInfo userInfo = new UserInfo(user.getNickname(), user.getScore(), user.getTotalGames(), user.getWinGames(), user.getIconUrl());
        return userInfo;
    }

    @PostMapping("/room/{roomId}/get_creator_name")
    @ResponseBody
    public String getCreatorName(@PathVariable String roomId){
        Room room = roomService.getRoom(roomId);
        return room.getCreator().getUsername();
    }

    // 处理玩家准备请求:
    @MessageMapping("/room/{roomId}/isReady")
    @SendTo("/topic/rooms.{roomId}")
    public GameMessage handleIsReady(@DestinationVariable String roomId, GameMessage message){
        roomService.handleReadyEvent(roomId, message.isReady());
        return message;
    }

    @PostMapping("/room/{roomId}/get_room_status")
    @ResponseBody
    public String getRoomStatus(@PathVariable String roomId){
        Room room = roomService.getRoom(roomId);
        if(room.isGameStarted()){
            return "START";
        }else{
            return "WAIT";
        }
    }

}
