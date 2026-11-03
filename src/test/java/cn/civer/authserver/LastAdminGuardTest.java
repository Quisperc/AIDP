package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.repository.UserRepository;
import cn.civer.authserver.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 最后一个管理员的护栏。
 *
 * 管理台没有找回入口：最后一个启用中的管理员被降级或停用后，只能改库恢复。
 * 表单对「自己那一行」和「别人那一行」长得一模一样，误操作成本极低，
 * 因此在 Service 层兜住，而不是靠按钮文案。
 */
@ActiveProfiles("test")
@SpringBootTest
class LastAdminGuardTest {

	@Autowired
	private UserAdminService userAdminService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearExistingAdmins() {
		// 用例只关心「当前只剩一个管理员」这个前提，先把历史数据里的管理员全部置为停用
		jdbcTemplate.update("UPDATE users SET enabled = false WHERE role = 'ROLE_ADMIN'");
	}

	@Test
	@Transactional
	@DisplayName("唯一管理员不能被停用、不能被降级")
	void lastAdminIsProtected() {
		User admin = userRepository.save(new User("solo-admin", passwordEncoder.encode("pwd-123"), "ROLE_ADMIN"));

		assertThrows(BusinessException.class,
				() -> userAdminService.update(admin.getId(), "ROLE_ADMIN", false, null),
				"停用唯一管理员应被拒绝");
		assertThrows(BusinessException.class,
				() -> userAdminService.update(admin.getId(), "ROLE_USER", true, null),
				"降级唯一管理员应被拒绝");
		assertThrows(BusinessException.class,
				() -> userAdminService.disable(admin.getId()),
				"停用接口同样应被拒绝");
	}

	@Test
	@Transactional
	@DisplayName("存在第二个管理员时，降级前一个应当放行")
	void demotionAllowedWhenAnotherAdminExists() {
		User first = userRepository.save(new User("admin-a", passwordEncoder.encode("pwd-123"), "ROLE_ADMIN"));
		userRepository.save(new User("admin-b", passwordEncoder.encode("pwd-123"), "ROLE_ADMIN"));

		userAdminService.update(first.getId(), "ROLE_USER", true, null);

		assert userRepository.findById(first.getId()).orElseThrow().getRole().equals("ROLE_USER");
	}
}
