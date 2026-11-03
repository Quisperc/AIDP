package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理台用户页的行为回归。
 *
 * 守两件事：
 * 1. **密码不出圈**。改成服务端渲染后，泄露面从「JSON 响应」变成了「模板变量」——
 *    只要有人把实体的 password 塞进 model，整页 HTML 就会带着 BCrypt 哈希出去。
 *    这里断言列表页既不含哈希前缀，也不含明文口令。
 * 2. **表单是 PRG 且失败不打断**。重名创建应重定向回列表并带错误消息，
 *    而不是抛 500 或停在半截页面。
 */
@SpringBootTest
class AdminUserPageTest {

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
	@Transactional
	@DisplayName("用户列表页不输出密码哈希或明文口令")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void listPageNeverExposesPassword() throws Exception {
		User user = new User("leak-check-user", passwordEncoder.encode("pwd-123"), "ROLE_USER");
		userRepository.save(user);

		mockMvc.perform(get("/admin/users"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("leak-check-user")))
				.andExpect(content().string(not(containsString("$2a$"))))
				.andExpect(content().string(not(containsString("pwd-123"))));
	}

	@Test
	@Transactional
	@DisplayName("创建用户成功：重定向回列表并带成功提示")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void createRedirectsWithSuccessFlash() throws Exception {
		mockMvc.perform(post("/admin/users").with(csrf())
						.param("username", "flash-ok-user")
						.param("password", "pwd-123")
						.param("role", "ROLE_USER"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/users"))
				.andExpect(flash().attribute("ok", containsString("flash-ok-user")));

		org.assertj.core.api.Assertions
				.assertThat(userRepository.findByUsername("flash-ok-user")).isPresent();
	}

	@Test
	@Transactional
	@DisplayName("用户名重复：重定向回列表并带错误提示，不抛 500")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void duplicateUsernameShowsErrorFlash() throws Exception {
		userRepository.save(new User("dup-user", passwordEncoder.encode("pwd-123"), "ROLE_USER"));

		mockMvc.perform(post("/admin/users").with(csrf())
						.param("username", "dup-user")
						.param("password", "pwd-123")
						.param("role", "ROLE_USER"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/users"))
				.andExpect(flash().attribute("error", containsString("已被使用")));
	}

	@Test
	@Transactional
	@DisplayName("编辑用户可停用，且不会被误启用")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void updateRespectsEnabledFlag() throws Exception {
		User user = userRepository.save(new User("toggle-user", passwordEncoder.encode("pwd-123"), "ROLE_USER"));

		// 不带 enabled 参数 = 取消勾选 = 停用
		mockMvc.perform(post("/admin/users/" + user.getId()).with(csrf())
						.param("role", "ROLE_USER"))
				.andExpect(status().is3xxRedirection());

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.isEnabled()).isFalse();
		// 密码留空不应被清空或改动
		org.assertj.core.api.Assertions.assertThat(reloaded.getPassword()).isNotBlank();
	}
}
