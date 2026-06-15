package martin.game.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import martin.game.model.User;
import martin.game.service.UserService;
import martin.game.utils.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final Logger logger = LogManager.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    //显示登陆页面
    @GetMapping("/login")
    public String login(@RequestParam(value="error", required = false) String error,
                        @RequestParam(value="logout", required = false) String logout,
                        Model model){
        if(error != null){
            System.out.println(error);
            model.addAttribute("error", "用户名或密码错误");
        }

        if(logout != null){
            model.addAttribute("message", "成功登出");
        }
        return "login";
    }

    //显示注册页面
    @GetMapping("/register")
    public String showRegistrationForm(Model model){
        model.addAttribute("user", new User());
        return "register";
    }

    // 处理注册请求
    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, Model model){
        try{
            // 调用服务层处理注册逻辑
            userService.register(user);
            model.addAttribute("message", "注册成功请登录");
            logger.info("用户:{} 注册账号成功!", user.getUsername());
            return "login";
        }catch (IllegalArgumentException e){
            // 异常
            model.addAttribute("error", e.getMessage());
            logger.error("用户:{} 注册账号失败! message:{}", user.getUsername(), e.getMessage());
            return "register";
        }
    }

    // 登录成功后的仪表盘页面
    @GetMapping("/index")
    public String dashboard(Model model) {
        logger.info(" 登录成功!");
        return "hall";
    }
}