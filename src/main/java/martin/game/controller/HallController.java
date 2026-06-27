package martin.game.controller;

import martin.game.model.Room;
import martin.game.model.User;
import martin.game.service.RoomService;
import martin.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

/*
    用于处理大厅页面的请求
 */

@Controller
public class HallController {

    @Autowired
    private RoomService roomService; // 后续会实现房间服务

    @Autowired
    private UserService userService;

    @PostMapping("/hall/getRoomList")
    @ResponseBody
    public Set<Room> getRoomList(){
        return roomService.getAvailableRooms();
    }

    @GetMapping("/hall")
    public String hall(Model model) {
        // 获取当前登录用户信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName(); // 获取用户名

        // 假设你的UserService有根据用户名获取用户信息的方法
        User currentUser = userService.findByUsername(username);

        // 将用户昵称添加到模型中，用于页面显示
        model.addAttribute("nickname", currentUser.getNickname());
        model.addAttribute("username", currentUser.getUsername());
        model.addAttribute("iconUrl", (currentUser.getIconUrl() != null) ? "/avatar/" + currentUser.getIconUrl() : "/icon/default-avatar.jpg");
        // 角色权限：注入有效角色给 Thymeleaf 条件渲染
        model.addAttribute("role", currentUser.getEffectiveRole().name());

        // 后续添加房间列表数据
        model.addAttribute("rooms", roomService.getAvailableRooms());

        return "hall";
    }

    @PostMapping("/hall/reconnect")
    @ResponseBody
    public String check_reconnect(@RequestBody String username){
        return roomService.check_reconnect(username);
    }

}
