package martin.game.controller;

import martin.game.model.User;
import martin.game.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Controller
public class UserController {

    @Value("${avatar.upload.path}")
    private String uploadPath; // 配置的相对路径（如 icon/）

    @Autowired
    private UserService userService;

    private static final Logger logger = LogManager.getLogger(UserController.class);

    @PostMapping("/user/profile/update")
    @ResponseBody
    public Map<String, Object> updateProfile(
            Authentication authentication, // Spring Security的认证信息（获取当前用户ID）
            String nickname, // 新昵称
            MultipartFile avatarFile // 头像文件（可选）
    ) throws IOException {
        Map<String, Object> result = new HashMap<>();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userService.findByUsername(userDetails.getUsername());

        if(!user.getNickname().equals(nickname)){
            logger.info("用户:" + user.getUsername() + " 从 " + user.getNickname() + " 更名为 " + nickname);
            try{
                if(!userService.updataNickName(user.getUsername(), nickname)){
                    result.put("success", false);
                    result.put("error", "昵称不合法");
                    return result;
                }
            }catch (Exception e){
                result.put("success", false);
                result.put("error", "昵称已存在!");
                return result;
            }

        }

        if(!avatarFile.isEmpty()){
            String contentType = avatarFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                result.put("success", false);
                result.put("error", "上传文件必须为JPG、PNG、GIF等");
                return result;
            }

            String originalFilename = avatarFile.getOriginalFilename();
            String fileExt = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = user.getUsername() + fileExt;

            Files.copy(avatarFile.getInputStream(), Paths.get(uploadPath, fileName), StandardCopyOption.REPLACE_EXISTING);
            result.put("avatarUrl", "avatar/" + fileName);
            logger.info(user.getUsername() + "上传了头像" + fileName);

            try{
                if(!userService.updataIcon(user.getUsername(), fileName)){
                    result.put("success", false);
                    result.put("error", "头像上传失败");
                    return result;
                }
            }catch (Exception e){
                result.put("success", false);
                result.put("error", "头像上传失败");
                return result;
            }
        }

        result.put("success", true);
        result.put("message", "更新信息完成!");
        return result;
    }
}
