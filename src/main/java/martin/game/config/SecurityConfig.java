package martin.game.config;

import jakarta.servlet.DispatcherType;
import martin.game.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final UserService userService;
    private static final Logger logger = LogManager.getLogger(SecurityConfig.class);


    public SecurityConfig(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 使用BCrypt加密算法
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider auth = new DaoAuthenticationProvider();
        auth.setUserDetailsService(userService);
        auth.setPasswordEncoder(passwordEncoder()); // 使用上面定义的密码编码器
        return auth;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // 错误页/转发由容器内部触发，必须放行，否则原始异常处理过程中会被 Security 二次拦截
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                // 放行 actuator 监控端点供 Prometheus 抓取；生产环境应改为仅内网/独立端口可访问
                // 静态资源全量 permitAll，避免 <img> / WebSocket 握手 401
                .requestMatchers("/", "/login", "/register",
                                 "/css/**", "/js/**", "/icon/**", "/avatar/**", "/poker/**", "/sound/**",
                                 "/druid/**", "/actuator/**", "/error").permitAll()
                // 管理后台：仅 ADMIN 可访问
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated() // 除了上述路径外，所有其他请求都需要登录认证（未登录会被拦截到登录页）。

        )
        // 登录配置
        .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/hall", true)  // 登录成功后跳转的页面
                .permitAll()   // 允许匿名访问登录相关接口（如提交登录表单的接口）
        )
        .logout(logout -> logout
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
        );

        return http.build(); //使所有配置生效
    }
}
