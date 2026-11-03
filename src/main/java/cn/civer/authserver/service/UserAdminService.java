package cn.civer.authserver.service;

import cn.civer.authserver.dto.UserResponse;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户管理业务逻辑。
 *
 * 原先散在 UserManagementController 里：密码编码、唯一性判断、软删除、改自己的账号。
 * 收拢到这里后，Controller 只剩参数校验与响应组装，业务规则可独立测试。
 */
@Service
public class UserAdminService {

	private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
	private static final String DEFAULT_ROLE = "ROLE_USER";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public List<UserResponse> listUsers() {
		return userRepository.findAll(Sort.by("id")).stream()
				.map(UserResponse::from)
				.toList();
	}

	@Transactional
	public UserResponse create(User request) {
		if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
			throw BusinessException.badRequest("用户名与密码均不能为空。");
		}
		String username = request.getUsername().trim();
		if (userRepository.findByUsername(username).isPresent()) {
			throw BusinessException.conflict("用户名已被使用: " + username);
		}
		User user = new User();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(request.getPassword()));
		user.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : DEFAULT_ROLE);
		user.setEnabled(true);
		log.info("[users] created: username={}", username);
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional
	public UserResponse update(Long id, User request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> BusinessException.notFound("用户不存在: " + id));
		if (StringUtils.hasText(request.getRole())) {
			user.setRole(request.getRole());
		}
		user.setEnabled(request.isEnabled());
		if (StringUtils.hasText(request.getPassword())) {
			user.setPassword(passwordEncoder.encode(request.getPassword()));
		}
		log.info("[users] updated: id={}", id);
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional
	public void disable(Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> BusinessException.notFound("用户不存在: " + id));
		user.setEnabled(false); // 软删除：保留授权与审计痕迹
		userRepository.save(user);
		log.info("[users] disabled: id={}", id);
	}

	/**
	 * 修改当前登录者自己的账号。
	 *
	 * 注意此处不广播全局登出：由客户端改完后重定向到 /oauth2/logout-all，
	 * 统一做一次退出，避免重复广播。
	 */
	@Transactional
	public UserResponse updateSelf(String currentUsername, User request) {
		User user = userRepository.findByUsername(currentUsername)
				.orElseThrow(() -> BusinessException.notFound("当前用户不存在: " + currentUsername));
		if (StringUtils.hasText(request.getUsername())) {
			String newUsername = request.getUsername().trim();
			userRepository.findByUsername(newUsername)
					.filter(other -> !other.getId().equals(user.getId()))
					.ifPresent(other -> {
						throw BusinessException.conflict("用户名已被使用: " + newUsername);
					});
			user.setUsername(newUsername);
		}
		if (StringUtils.hasText(request.getPassword())) {
			user.setPassword(passwordEncoder.encode(request.getPassword()));
		}
		log.info("[users] self-updated: username={}", currentUsername);
		return UserResponse.from(userRepository.save(user));
	}
}
