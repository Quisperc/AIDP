package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.AuthorizationQueryRepository;
import cn.civer.authserver.repository.UserRepository;
import cn.civer.authserver.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号自助 + 单客户端撤销的回归。
 *
 * 守四件事：
 * 1. 普通用户能进 /account，但 /admin/** 仍然拒绝；
 * 2. 改密必须校验原密码（会话被借用时不能直接改掉凭据）；
 * 3. 资料修改能落库且邮箱标记为未验证；
 * 4. 撤销只影响「当前用户 + 指定客户端」，不牵连其它主体与其它客户端。
 */
@ActiveProfiles("test")
@SpringBootTest
class AccountFlowTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	@Autowired
	private AuthorizationQueryRepository authorizationQueryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AccountService accountService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	@Transactional
	@DisplayName("普通用户可打开账号页，但管理台仍然 403")
	@WithMockUser(username = "self-user", authorities = {"ROLE_USER"})
	void normalUserCanUseAccountPage() throws Exception {
		userRepository.save(new User("self-user", passwordEncoder.encode("pwd-123"), "ROLE_USER"));

		mockMvc.perform(get("/account"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("修改密码")))
				.andExpect(content().string(containsString("已授权应用")));
		mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
	}

	@Test
	@Transactional
	@DisplayName("原密码错误时改密被拒绝，凭据不变")
	@WithMockUser(username = "pwd-user", authorities = {"ROLE_USER"})
	void passwordChangeRequiresCurrentPassword() throws Exception {
		User user = userRepository.save(
				new User("pwd-user", passwordEncoder.encode("right-pwd"), "ROLE_USER"));

		mockMvc.perform(post("/account/password").with(csrf())
						.param("currentPassword", "wrong-pwd")
						.param("newPassword", "brand-new-123")
						.param("confirmPassword", "brand-new-123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("原密码")));

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("right-pwd", reloaded.getPassword())).isTrue();
	}

	@Test
	@Transactional
	@DisplayName("自助填写邮箱落库后标记为未验证")
	@WithMockUser(username = "profile-user", authorities = {"ROLE_USER"})
	void selfFilledEmailIsNotVerified() throws Exception {
		User user = userRepository.save(
				new User("profile-user", passwordEncoder.encode("pwd-123"), "ROLE_USER"));

		mockMvc.perform(post("/account/profile").with(csrf())
						.param("email", "me@example.com")
						.param("nickname", "小陈"))
				.andExpect(status().is3xxRedirection());

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo("me@example.com");
		assertThat(reloaded.getNickname()).isEqualTo("小陈");
		assertThat(reloaded.isEmailVerified()).isFalse();
	}

	@Test
	@Transactional
	@DisplayName("撤销只删除目标客户端的 consent 与 authorization")
	void revokeIsScopedToSingleClientAndPrincipal() {
		registeredClientRepository.save(RegisteredClient.withId("client-a")
				.clientId("app-a")
				.clientSecret("{noop}secret-for-app-a")
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("https://app-a.example.com/cb")
				.scope("openid")
				.build());
		registeredClientRepository.save(RegisteredClient.withId("client-b")
				.clientId("app-b")
				.clientSecret("{noop}secret-for-app-b")
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("https://app-b.example.com/cb")
				.scope("openid")
				.build());

		insertConsent("client-a", "owner");
		insertAuthorization("auth-a", "client-a", "owner");
		insertConsent("client-b", "owner");
		insertAuthorization("auth-b", "client-b", "owner");
		insertConsent("client-a", "bystander");
		insertAuthorization("auth-c", "client-a", "bystander");

		int revoked = authorizationQueryRepository.revokeClient("owner", "app-a");

		assertThat(revoked).isEqualTo(1);
		assertThat(countAuthorizations("owner", "client-a")).isZero();
		assertThat(countConsents("owner", "client-a")).isZero();
		assertThat(countAuthorizations("owner", "client-b")).isEqualTo(1);
		assertThat(countConsents("owner", "client-b")).isEqualTo(1);
		assertThat(countAuthorizations("bystander", "client-a")).isEqualTo(1);
		assertThat(countConsents("bystander", "client-a")).isEqualTo(1);
		// 不存在的客户端不能撤销任何记录
		assertThat(authorizationQueryRepository.revokeClient("owner", "not-registered")).isZero();
	}

	@Test
	@Transactional
	@DisplayName("discovery 显式广告 profile 与 email")
	@WithMockUser(username = "any", authorities = {"ROLE_USER"})
	void discoveryAdvertisesScopes() throws Exception {
		mockMvc.perform(get("/.well-known/openid-configuration"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scopes_supported", hasItem("profile")))
				.andExpect(jsonPath("$.scopes_supported", hasItem("email")));
	}

	@Test
	@Transactional
	@DisplayName("改密成功：旧凭据立即失效，新凭据可用")
	void passwordChangeReplacesCredential() {
		User user = userRepository.save(new User("pwd-ok-user",
				passwordEncoder.encode("old-pwd-123"), "ROLE_USER"));

		accountService.changePassword("pwd-ok-user", "old-pwd-123", "new-pwd-456", "new-pwd-456");

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("old-pwd-123", reloaded.getPassword())).isFalse();
		assertThat(passwordEncoder.matches("new-pwd-456", reloaded.getPassword())).isTrue();
	}

	@Test
	@Transactional
	@DisplayName("改登录名：登录名变化但 sub 不变，旧名不再可登录")
	void changeUsernameKeepsSubject() {
		User user = userRepository.save(new User("old-name",
				passwordEncoder.encode("pwd-123"), "ROLE_USER"));
		String publicId = user.getPublicId();

		String newName = accountService.changeUsername("old-name", "new-name", "pwd-123");

		assertThat(newName).isEqualTo("new-name");
		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getUsername()).isEqualTo("new-name");
		assertThat(reloaded.getPublicId()).isEqualTo(publicId);
		assertThat(userRepository.findByUsername("old-name")).isEmpty();
		assertThat(userRepository.findByUsername("new-name")).isPresent();
	}

	private void insertConsent(String internalId, String principal) {
		jdbcTemplate.update("INSERT INTO oauth2_authorization_consent "
						+ "(registered_client_id, principal_name, authorities) VALUES (?, ?, ?)",
				internalId, principal, "SCOPE_openid");
	}

	private void insertAuthorization(String id, String internalId, String principal) {
		jdbcTemplate.update("INSERT INTO oauth2_authorization "
						+ "(id, registered_client_id, principal_name, authorization_grant_type, authorized_scopes) "
						+ "VALUES (?, ?, ?, ?, ?)",
				id, internalId, principal, "authorization_code", "openid");
	}

	private int countConsents(String principal, String internalId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization_consent "
				+ "WHERE principal_name = ? AND registered_client_id = ?", Integer.class, principal, internalId);
	}

	private int countAuthorizations(String principal, String internalId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization "
				+ "WHERE principal_name = ? AND registered_client_id = ?", Integer.class, principal, internalId);
	}
}
