package cn.civer.authserver.service;

import cn.civer.authserver.dto.AuthorizationView;
import cn.civer.authserver.dto.UserResponse;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.repository.AuthorizationQueryRepository;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 账号自助：当前登录者维护自己的资料、凭据、登录名与已授权应用。
 *
 * 权限边界：所有方法都以「当前会话的登录名」为主体，不接收用户 id，
 * 因此不存在改到别人的路径。管理员与普通用户走同一套逻辑，
 * 区别只在页面上是否显示管理台导航。
 *
 * 改密码 / 改登录名都要求原密码并强制全局登出：
 * - 要求原密码是为了防止「拿到一个未锁屏会话就能接管账号」；
 * - 强制全局登出会吊销全部令牌与同意，避免旧凭据继续有效；
 * - 内部 principal_name 是可变登录名，改名时按**旧名**吊销，
 *   才能清掉以旧名挂着的 authorization / consent，不留孤儿记录。
 * 协议层的 sub 是不可变 public_id（见 TokenConfig），客户端账号关联不受改名影响。
 */
@Service
public class AccountService {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptService loginAttemptService;
	private final SsoLogoutService ssoLogoutService;
	private final AuthorizationQueryRepository authorizationQueryRepository;
	private final AuditService auditService;

	public AccountService(UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			LoginAttemptService loginAttemptService,
			SsoLogoutService ssoLogoutService,
			AuthorizationQueryRepository authorizationQueryRepository,
			AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.loginAttemptService = loginAttemptService;
		this.ssoLogoutService = ssoLogoutService;
		this.authorizationQueryRepository = authorizationQueryRepository;
		this.auditService = auditService;
	}

	public UserResponse current(String principalName) {
		return UserResponse.from(require(principalName));
	}

	/** 资料更新不改变凭据，因此不触发全局登出。 */
	@Transactional
	public void updateProfile(String principalName, String email, String nickname) {
		User user = require(principalName);
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
				// 自助填写的邮箱未经验证，不能给下游账号合并提供依据
				user.setEmailVerified(false);
			}
		} else {
			user.setEmail(null);
			user.setEmailVerified(false);
		}
		user.setNickname(normalize(nickname));
		userRepository.save(user);
		auditService.record(AuditService.ACCOUNT_UPDATE, principalName, "profileUpdated=true");
	}

	@Transactional
	public void changePassword(String principalName, String currentPassword,
			String newPassword, String confirmPassword) {
		User user = verifyCredentials(principalName, currentPassword);
		if (!StringUtils.hasText(newPassword) || newPassword.length() < MIN_PASSWORD_LENGTH) {
			throw BusinessException.badRequest("新密码至少需要 " + MIN_PASSWORD_LENGTH + " 位。");
		}
		if (!newPassword.equals(confirmPassword)) {
			throw BusinessException.badRequest("两次输入的新密码不一致。");
		}
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		loginAttemptService.clear(principalName);
		auditService.record(AuditService.ACCOUNT_UPDATE, principalName, "passwordChanged=true");
		ssoLogoutService.performGlobalLogout(principalName);
	}

	/** 返回新登录名，供控制器展示；调用方随后清会话并要求重新登录。 */
	@Transactional
	public String changeUsername(String principalName, String newUsername, String currentPassword) {
		User user = verifyCredentials(principalName, currentPassword);
		String target = normalize(newUsername);
		if (target == null) {
			throw BusinessException.badRequest("新用户名不能为空。");
		}
		if (target.equals(user.getUsername())) {
			throw BusinessException.badRequest("新用户名与当前用户名相同。");
		}
		userRepository.findByUsername(target)
				.filter(other -> !other.getId().equals(user.getId()))
				.ifPresent(other -> {
					throw BusinessException.conflict("用户名已被使用: " + target);
				});
		String oldName = user.getUsername();
		user.setUsername(target);
		userRepository.save(user);
		auditService.record(AuditService.ACCOUNT_UPDATE, oldName, "usernameChanged=" + target);
		// 按旧名吊销：authorization / consent 的 principal_name 挂的是旧登录名
		ssoLogoutService.performGlobalLogout(oldName);
		return target;
	}

	public List<AuthorizationView> authorizations(String principalName) {
		return authorizationQueryRepository.listForPrincipal(principalName);
	}

	/**
	 * 撤销单个客户端的授权，只作用于当前用户。
	 *
	 * 这里刻意不调用 performGlobalLogout（那会清掉该用户在所有客户端的授权）。
	 * 认证中心只做服务端令牌吊销，不实现任何私有登出推送；撤销后令牌即失效，
	 * 客户端本地会话由用户在应用内自行退出（标准的 RP-Initiated Logout）。
	 */
	@Transactional
	public int revoke(String principalName, String clientId) {
		if (!StringUtils.hasText(clientId)) {
			throw BusinessException.badRequest("客户端标识不能为空。");
		}
		int revoked = authorizationQueryRepository.revokeClient(principalName, clientId);
		if (revoked == 0) {
			// 该用户没有授权给这个客户端时，consent 可能还在但无令牌记录；
			// 仍然记录一次操作，避免用户反复点击却看不到反馈
			auditService.record(AuditService.AUTHZ_REVOKE, principalName, "clientId=" + clientId + ", noActiveTokens");
			return 0;
		}
		auditService.record(AuditService.AUTHZ_REVOKE, principalName,
				"clientId=" + clientId + ", revokedTokenRecords=" + revoked);
		return revoked;
	}

	/** 原密码校验复用登录失败限流：连续猜错会临时锁定，成功则清零。 */
	private User verifyCredentials(String principalName, String currentPassword) {
		User user = require(principalName);
		if (loginAttemptService.isBlocked(principalName)) {
			throw BusinessException.badRequest("尝试过于频繁，请稍后再试。");
		}
		if (!StringUtils.hasText(currentPassword)
				|| !passwordEncoder.matches(currentPassword, user.getPassword())) {
			loginAttemptService.recordFailure(principalName);
			throw BusinessException.badRequest("原密码不正确。");
		}
		return user;
	}

	private User require(String principalName) {
		return userRepository.findByUsername(principalName)
				.orElseThrow(() -> BusinessException.notFound("当前用户不存在: " + principalName));
	}

	private static String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
