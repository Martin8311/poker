package martin.game.config;

import martin.game.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
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
                // 放行 actuator 监控端点供 Prometheus 抓取；生产环境应改为仅内网/独立端口可访问
                .requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/druid/**", "/actuator/**").permitAll() // permitAll 定义了可以匿名访问的资源
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
