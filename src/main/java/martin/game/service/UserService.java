package martin.game.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import martin.game.model.User;
import martin.game.repository.UserRepository;
import martin.game.utils.LoginUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    // private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println(username);
        // 1. 根据用户名查询数据库中的用户实体
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));

        // 2. 返回自定义的 LoginUser（包含用户 ID）
        return new LoginUser(user);
    }

    public User findByUsername(String username){
        return userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    // 用户注册
    public void register(User user){
        if(userRepository.existsByUsername(user.getUsername())){
            throw new IllegalArgumentException("用户名已存在");
        }

//        if(userRepository.existsByEmail(user.getEmail())){
//            throw new IllegalArgumentException("邮箱已存在");
//        }

        if(userRepository.existsByNickname(user.getNickname())){
            throw new IllegalArgumentException("昵称已存在");
        }


        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepository.save(user);
    }

    /**
     * 更新用户游戏统计（对局数、胜局数、积分）
     * @param username 用户名
     * @param scoreChange 积分变化（正数增加，负数减少）
     */
    @Transactional
    public void updateUserScoreInfo(String username, int scoreChange){
        userRepository.updateScoreByUsername(username, scoreChange > 0 ? 1 : 0, scoreChange);
    }

    /**
     * 更新玩家个人资料 头像
     * @return
     */
    @Transactional
    public boolean updataNickName (String username, String nickname) throws Exception{
        int affectedRows = userRepository.updateNickNameByUsername(username, nickname);
        return affectedRows == 1;
    }

    @Transactional
    public boolean updataIcon(String username, String iconUrl) throws Exception{
        int affectedRows = userRepository.updateIconUrlByUsername(username, iconUrl);
        return affectedRows == 1;
    }

}
