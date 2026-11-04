package cn.civer.authserver.service;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import cn.civer.authserver.security.AccountUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 表单登录的身份来源：按登录名查库，转换成携带稳定主体标识的 UserDetails。
 *
 * 入参 username 是登录表单里那个可变的人名；返回的 UserDetails.getUsername()
 * 是不可变 public_id（见 AccountUserDetails）。DaoAuthenticationProvider 的
 * 口令校验用输入密码比对 userDetails.getPassword()，与名字语义无关。
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
		return new AccountUserDetails(user);
	}
}
