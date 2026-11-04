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
 * 表单登录的身份来源：按登录名查库，返回 Spring Security 内置的 UserDetails。
 *
 * 关键约束：主体只能用框架自带类型，不能塞自定义实现。
 * Spring Authorization Server 会把 {@code java.security.Principal} 写进
 * oauth2_authorization.attributes，再用 Jackson 3 的 PolymorphicTypeValidator
 * 反序列化；自定义 UserDetails 不在白名单内，读取时会抛 InvalidTypeIdException
 * （线上表现：提交同意页 POST /oauth2/authorize 返回 500）。
 *
 * 账号资料（public_id / email / nickname / email_verified）不进入安全主体，
 * 改由 TokenConfig 与 FooterModelAdvice 按登录名从库中读取。
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
				user.isEnabled(),
				true, true, true,
				List.of(new SimpleGrantedAuthority(user.getRole())));
	}
}
