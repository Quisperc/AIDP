package cn.civer.authserver.service;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 表单登录的身份来源：把库里的用户转换成 Spring Security 的 UserDetails。
 *
 * 这里只做「查用户 → 转身份」一件事。PasswordEncoder 的 bean 定义已移到
 * {@link cn.civer.authserver.config.SecurityConfig} —— 编码器是安全基础设施，
 * 不该由一个业务服务生产。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public CustomUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));

		return new org.springframework.security.core.userdetails.User(
				user.getUsername(),
				user.getPassword(),
				user.isEnabled(), true, true, true,
				List.of(new SimpleGrantedAuthority(user.getRole())));
	}
}
