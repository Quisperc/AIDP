package cn.civer.authserver.service;

import cn.civer.authserver.dto.PageResult;
import cn.civer.authserver.dto.UserResponse;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户管理业务逻辑（仅管理台，ROLE_ADMIN 由 URL 规则保证）。
 *
 * 入参一律用字符串/布尔，不再接收 {@link User} 实体：
 * 实体作为请求体时，「字段没传」和「传了默认值」区分不开（enabled 曾因此在
 * 只改角色时把停用用户重新启用）。表单语义下由调用方显式给出每个字段更准确。
 *
 * 账号自助（改自己的资料/密码/登录名/撤销授权）在 AccountService：
 * 那条路径要求原密码校验与强制登出，和「管理员改别人」的规则完全不同，不共用方法。
 */
@Service
public class UserAdminService {

	private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
	private static final String DEFAULT_ROLE = "ROLE_USER";
	private static final String ADMIN_ROLE = "ROLE_ADMIN";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public PageResult<UserResponse> listUsers(int page, int size) {
		long total = userRepository.count();
		int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
		// 页码越界（例如刚删完最后一页的数据）时回到最后一页，而不是给一张空表
		int safePage = total == 0 ? 0 : Math.min(Math.max(page, 0), totalPages - 1);
		List<UserResponse> items = userRepository
				.findAll(PageRequest.of(safePage, size, Sort.by("id")))
				.stream()
				.map(UserResponse::from)
				.toList();
		return PageResult.of(items, safePage, size, total);
	}

	public UserResponse get(Long id) {
		return userRepository.findById(id)
				.map(UserResponse::from)
				.orElseThrow(() -> BusinessException.notFound("用户不存在: " + id));
	}

	@Transactional
	public UserResponse create(String rawUsername, String rawPassword, String role,
			String email, String nickname) {
		if (!StringUtils.hasText(rawUsername) || !StringUtils.hasText(rawPassword)) {
			throw BusinessException.badRequest("用户名与密码均不能为空。");
		}
		String username = rawUsername.trim();
		if (userRepository.findByUsername(username).isPresent()) {
			throw BusinessException.conflict("用户名已被使用: " + username);
		}
		String newEmail = EmailPolicy.normalize(email);
		if (newEmail != null) {
			// 管理台此前只查重、不校验格式，脏邮箱会直接进 email claim；两条写入路径统一在这里兜住
			EmailPolicy.requireValid(newEmail);
			if (userRepository.findByEmailIgnoreCase(newEmail).isPresent()) {
				throw BusinessException.conflict("该邮箱已被其他账号使用。");
			}
		}
		User user = new User();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(rawPassword));
		user.setRole(StringUtils.hasText(role) ? role : DEFAULT_ROLE);
		user.setEnabled(true);
		user.setEmail(newEmail);
		user.setNickname(normalize(nickname));
		// 管理员录入的邮箱视为可信来源；用户自助填写的邮箱由 AccountService 标为未验证
		user.setEmailVerified(newEmail != null);
		log.info("[users] created: username={}", username);
		return UserResponse.from(userRepository.save(user));
	}

	/**
	 * 更新角色、启用状态、资料，可选重置密码。
	 *
	 * @param newPassword 为空表示不修改
	 * @param email       为空表示清空邮箱；填写时视为管理员录入（已验证）
	 */
	@Transactional
	public UserResponse update(Long id, String role, boolean enabled, String newPassword,
			String email, String nickname) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> BusinessException.notFound("用户不存在: " + id));
		String targetRole = StringUtils.hasText(role) ? role : user.getRole();
		assertNotLastEnabledAdmin(user, targetRole, enabled);
		user.setRole(targetRole);
		user.setEnabled(enabled);
		if (StringUtils.hasText(newPassword)) {
			user.setPassword(passwordEncoder.encode(newPassword));
		}
		String newEmail = EmailPolicy.normalize(email);
		if (newEmail != null) {
			EmailPolicy.requireValid(newEmail);
			userRepository.findByEmailIgnoreCase(newEmail)
					.filter(other -> !other.getId().equals(user.getId()))
					.ifPresent(other -> {
						throw BusinessException.conflict("该邮箱已被其他账号使用。");
					});
			if (!newEmail.equals(user.getEmail())) {
				user.setEmail(newEmail);
				user.setEmailVerified(true);
			}
		} else {
			user.setEmail(null);
			user.setEmailVerified(false);
		}
		user.setNickname(normalize(nickname));
		log.info("[users] updated: id={}, role={}, enabled={}", id, targetRole, enabled);
		return UserResponse.from(userRepository.save(user));
	}

	/**
	 * 停用（软删除）：保留授权与审计痕迹，不做物理删除。
	 * 按钮文案写「停用」，路由仍是 /delete，两者在文档里都有说明。
	 */
	@Transactional
	public void disable(Long id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> BusinessException.notFound("用户不存在: " + id));
		assertNotLastEnabledAdmin(user, user.getRole(), false);
		user.setEnabled(false);
		userRepository.save(user);
		log.info("[users] disabled: id={}", id);
	}

	/**
	 * 不允许把最后一个启用中的管理员停用或降级。
	 *
	 * 管理台没有「找回管理员」的入口，最后一个管理员被拿掉后只能改库恢复；
	 * 而表单本身对「自己的那一行」和「别人的那一行」长得一模一样，
	 * 误操作成本极低，因此在服务端兜住。
	 */
	private void assertNotLastEnabledAdmin(User user, String targetRole, boolean targetEnabled) {
		boolean currentlyAdmin = ADMIN_ROLE.equals(user.getRole()) && user.isEnabled();
		boolean remainsAdmin = ADMIN_ROLE.equals(targetRole) && targetEnabled;
		if (currentlyAdmin && !remainsAdmin
				&& userRepository.countByRoleAndEnabledTrue(ADMIN_ROLE) <= 1) {
			throw BusinessException.conflict(
					"这是最后一个启用中的管理员，不能停用或降级。请先给其他账号授予管理员权限。");
		}
	}

	private static String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
