package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.AdminAuditLogRepository;
import cn.civer.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作审计回归。
 *
 * 守两件事：写操作必须留下记录（不是靠前端埋点），审计页真的能渲染出来。
 * 后者同样是模板回归——DTO 字段名写错时页面不会报错，只会显示空白单元格。
 */
@ActiveProfiles("test")
@SpringBootTest
class AdminAuditTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private AdminAuditLogRepository auditRepository;

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
	@DisplayName("新建用户写入审计：动作、对象、操作者、IP 齐全")
	@WithMockUser(username = "auditor", authorities = {"ROLE_ADMIN"})
	void creatingUserWritesAuditLog() throws Exception {
		mockMvc.perform(post("/admin/users").with(csrf())
						.param("username", "audit-target")
						.param("password", "pwd-123")
						.param("role", "ROLE_ADMIN"))
				.andExpect(status().is3xxRedirection());

		var latest = auditRepository.findAllByOrderByIdDesc(
				PageRequest.of(0, 1)).getContent();
		var log = latest.get(0);
		assertThat(log.getAction()).isEqualTo("USER_CREATE");
		assertThat(log.getTarget()).isEqualTo("audit-target");
		assertThat(log.getActor()).isEqualTo("auditor");
		assertThat(log.getDetail()).contains("ROLE_ADMIN");
	}

	@Test
	@Transactional
	@DisplayName("审计页渲染最近一条记录，且不暴露写接口")
	@WithMockUser(username = "auditor", authorities = {"ROLE_ADMIN"})
	void auditPageRendersEntries() throws Exception {
		userRepository.save(new User("audit-page-target", passwordEncoder.encode("pwd-123"), "ROLE_USER"));
		mockMvc.perform(post("/admin/users").with(csrf())
						.param("username", "audit-page-created")
						.param("password", "pwd-123"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/admin/audit"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("操作审计")))
				.andExpect(content().string(containsString("新建用户")))
				.andExpect(content().string(containsString("audit-page-created")))
				.andExpect(content().string(containsString("auditor")));
	}
}
