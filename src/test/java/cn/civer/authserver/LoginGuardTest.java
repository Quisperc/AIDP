package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录失败限流。
 *
 * 阈值压到 3 次，避免用例里真跑 10 轮。断言两件事：
 * 1. 普通账号连续失败达到阈值后，**即使密码正确**也会被拦下（拦的是猜解行为本身）；
 * 2. 管理员账号不参与限流——被锁后没有自助解锁入口，属于运维事故。
 *
 * 计数落在 Cache 而非数据库，用例之间不会回滚，因此每个用例用独立用户名。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"app.login-guard.max-attempts=3", "app.login-guard.window=15m"})
class LoginGuardTest {

	private static final int MAX_ATTEMPTS = 3;
	private static final String PASSWORD = "pwd-123";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.webAppContextSetup(context)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	@DisplayName("普通账号连续失败达到阈值后，正确密码也被拦截")
	void normalUserIsLockedAfterFailures() throws Exception {
		userRepository.save(new User("guard-victim", passwordEncoder.encode(PASSWORD), "ROLE_USER"));

		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			mockMvc.perform(post("/login").with(csrf())
							.param("username", "guard-victim")
							.param("password", "wrong-password"))
					.andExpect(status().is3xxRedirection());
		}

		// 第 N+1 次即使密码正确，也在认证前被重定向到 ?blocked
		mockMvc.perform(post("/login").with(csrf())
						.param("username", "guard-victim")
						.param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?blocked"));

		mockMvc.perform(get("/login").param("blocked", ""))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("管理员账号不限流")
	void adminIsNeverLocked() throws Exception {
		userRepository.save(new User("guard-admin", passwordEncoder.encode(PASSWORD), "ROLE_ADMIN"));

		for (int i = 0; i < MAX_ATTEMPTS + 2; i++) {
			mockMvc.perform(post("/login").with(csrf())
							.param("username", "guard-admin")
							.param("password", "wrong-password"))
					.andExpect(status().is3xxRedirection());
		}

		// 管理员用正确密码仍然能登录（落到 / 或保存的请求上），不会被 ?blocked 拦下
		mockMvc.perform(post("/login").with(csrf())
						.param("username", "guard-admin")
						.param("password", PASSWORD))
				.andExpect(status().is3xxRedirection());
	}
}
