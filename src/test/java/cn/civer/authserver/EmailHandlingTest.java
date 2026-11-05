package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.repository.UserRepository;
import cn.civer.authserver.service.AccountService;
import cn.civer.authserver.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 邮箱写入链路与 V4 数据迁移的回归。
 *
 * 守四件事：
 * 1. 自助与管理台两条写入路径都归一化邮箱（此前只有 trim，大小写可绕过唯一性）；
 * 2. 忽略大小写查重，同一邮箱不能因大小写不同落到两个账号；
 * 3. 两条路径都做标准格式校验（管理台原先只查重、不校验）；
 * 4. V4 脚本能在 H2 上真实执行，且正确消解大小写重复、规整存量。
 */
@ActiveProfiles("test")
@SpringBootTest
class EmailHandlingTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserAdminService userAdminService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	@Transactional
	@DisplayName("自助填写的混合大小写邮箱落库为小写，且仍标记为未验证")
	@WithMockUser(username = "mail-self", authorities = {"ROLE_USER"})
	void selfFilledEmailIsNormalized() throws Exception {
		User user = userRepository.save(
				new User("mail-self", passwordEncoder.encode("pwd-123"), "ROLE_USER"));

		mockMvc.perform(post("/account/profile").with(csrf())
						.param("email", "  ME@Example.COM ")
						.param("nickname", "小陈"))
				.andExpect(status().is3xxRedirection());

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo("me@example.com");
		assertThat(reloaded.isEmailVerified()).isFalse();
	}

	@Test
	@Transactional
	@DisplayName("管理员录入同样归一化，并保持「已验证」语义")
	void adminInputIsNormalizedAndVerified() {
		userAdminService.create("mail-admin-created", "pwd-123", "ROLE_USER",
				"  ADMIN@Example.COM ", null);

		User reloaded = userRepository.findByUsername("mail-admin-created").orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo("admin@example.com");
		assertThat(reloaded.isEmailVerified()).isTrue();
	}

	@Test
	@Transactional
	@DisplayName("仅大小写不同的同一邮箱不能登记到两个账号")
	void caseInsensitiveUniquenessAcrossBothPaths() {
		userAdminService.create("mail-owner-a", "pwd-123", "ROLE_USER", "Shared@Example.com", null);
		userRepository.save(new User("mail-owner-b", passwordEncoder.encode("pwd-123"), "ROLE_USER"));
		userRepository.flush(); // 让 IgnoreCase 查询一定能看到上一步的写入

		assertThatThrownBy(() -> accountService.updateProfile("mail-owner-b", "shared@example.com", null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("已被其他账号使用");
	}

	@Test
	@Transactional
	@DisplayName("非法邮箱在自助与管理台两条路径都被拒绝")
	void malformedEmailRejectedOnBothPaths() {
		userRepository.save(new User("mail-bad-user", passwordEncoder.encode("pwd-123"), "ROLE_USER"));

		assertThatThrownBy(() -> accountService.updateProfile("mail-bad-user", "not-an-email", null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("邮箱格式");
		assertThatThrownBy(() -> userAdminService.create(
				"mail-bad-admin", "pwd-123", "ROLE_USER", "a@@b.com", null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("邮箱格式");
	}

	@Test
	@Transactional
	@DisplayName("V4 迁移消解大小写重复，并把存量邮箱统一小写")
	void v4MigrationNormalizesLegacyRows() throws Exception {
		// 模拟历史脏数据：同一邮箱仅大小写不同，在大小写敏感的唯一索引下可以并存
		insertLegacyUser("v4-keeper", "Legacy@Example.COM");
		insertLegacyUser("v4-dup", "LEGACY@example.com");
		insertLegacyUser("v4-mixed", "MiXeD@Example.COM");

		// 真跑一遍 V4 脚本：既验证 SQL 语法在 H2(MODE=PostgreSQL) 下可执行，
		// 也验证冲突消解与小写化的业务逻辑（启动时的空库迁移覆盖不到后者）
		Connection connection = DataSourceUtils.getConnection(dataSource);
		ScriptUtils.executeSqlScript(connection, new EncodedResource(
				new ClassPathResource("db/migration/V4__normalize_user_email.sql"),
				StandardCharsets.UTF_8));

		assertThat(emailOf("v4-keeper")).isEqualTo("legacy@example.com");
		assertThat(emailOf("v4-dup")).isNull();
		assertThat(emailOf("v4-mixed")).isEqualTo("mixed@example.com");
		assertThat(verifiedOf("v4-keeper")).isTrue();
		assertThat(verifiedOf("v4-dup")).isFalse();
	}

	private void insertLegacyUser(String username, String email) {
		jdbcTemplate.update("INSERT INTO users "
						+ "(username, password, role, enabled, public_id, email, email_verified) "
						+ "VALUES (?, ?, 'ROLE_USER', true, ?, ?, true)",
				username, "not-a-real-hash", UUID.randomUUID().toString(), email);
	}

	private String emailOf(String username) {
		return jdbcTemplate.query(
				"SELECT email FROM users WHERE username = ?",
				rs -> rs.next() ? rs.getString(1) : null, username);
	}

	private Boolean verifiedOf(String username) {
		return jdbcTemplate.queryForObject(
				"SELECT email_verified FROM users WHERE username = ?", Boolean.class, username);
	}
}
