package martin.game.config;

import martin.game.interceptor.BaseInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private BaseInterceptor baseInterceptor;

    @Value("${avatar.upload.path:./uploads/avatar/}")
    private String avatarUploadPath; // 外部存储路径：D:/avatar/icon/

    @Value("${avatar.access.prefix:/avatar/}")
    private String avatarAccessPrefix; // 前端访问前缀：/avatar/

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 关键：将 "/avatar/**" URL 映射到外部目录 "file:D:/avatar/icon/"
        // 注意：外部路径必须加 "file:" 前缀，表示是本地磁盘文件
        registry.addResourceHandler(avatarAccessPrefix + "**")
                .addResourceLocations("file:" + avatarUploadPath);
    }

    // TODO: 通过拦截器 实现pc端 移动端页面不同 无法实现
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(baseInterceptor)
//                .addPathPatterns("/**")
//                .excludePathPatterns("/login")
//                .excludePathPatterns("/hall");
//    }
}
