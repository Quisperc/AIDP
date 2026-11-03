package cn.civer.authserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理台越权回归测试。
 *
 * 背景：AuthServerApplication 曾缺少 @EnableMethodSecurity，导致控制器上的
 * {@code @PreAuthorize("hasAuthority('ROLE_ADMIN')")} 静默失效——不报错、也不生效，
 * 任意登录用户都能调管理接口。管理台并入本应用后，权限收到了 SecurityConfig 的
 * URL 规则上（/admin/** 需要 ROLE_ADMIN），这个测试守的就是那条规则。
 *
 * 同时也是**模板渲染测试**：管理员访问的用例会真的走一遍 Thymeleaf，
 * DTO 字段名写错、fragment 参数不匹配之类的问题会在这里直接暴露，
 * 不必等到部署后打开浏览器才发现。
 */
@ActiveProfiles("test")
@SpringBootTest
class AdminAuthorizationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.webAppContextSetup(context)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	@DisplayName("普通用户不得进入用户管理页")
	@WithMockUser(username = "user", authorities = {"ROLE_USER"})
	void normalUserIsDeniedOnUsers() throws Exception {
		mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("普通用户不得进入客户端管理页")
	@WithMockUser(username = "user", authorities = {"ROLE_USER"})
	void normalUserIsDeniedOnClients() throws Exception {
		mockMvc.perform(get("/admin/clients")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("普通用户不得进入账号页")
	@WithMockUser(username = "user", authorities = {"ROLE_USER"})
	void normalUserIsDeniedOnProfile() throws Exception {
		mockMvc.perform(get("/admin/profile")).andExpect(status().isForbidden());
	}

	@Test
	@Transactional
	@DisplayName("管理员可以打开用户管理页并看到列表结构")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void adminSeesUserList() throws Exception {
		// 自造一行数据：空表时页面只渲染空状态，表头根本不存在。
		// 这里原本依赖启动时预置的演示账号，一旦关闭 seed-demo-users 用例就会假失败。
		userRepository.save(new User("listed-user", "{bcrypt}placeholder", "ROLE_USER"));

		mockMvc.perform(get("/admin/users"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("新增用户")))
				.andExpect(content().string(containsString("用户名")))
				.andExpect(content().string(containsString("listed-user")));
	}

	@Test
	@DisplayName("管理员可以打开客户端管理页")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void adminSeesClientList() throws Exception {
		mockMvc.perform(get("/admin/clients"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("注册客户端")));
	}

	@Test
	@DisplayName("管理员可以打开新增用户表单")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void adminSeesUserForm() throws Exception {
		mockMvc.perform(get("/admin/users/new"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("初始密码")));
	}

	@Test
	@DisplayName("管理员可以打开编辑页，且表单会回显该用户")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	@Transactional
	void adminSeesEditForm() throws Exception {
		User target = userRepository.save(new User("edit-target",
				"{bcrypt}placeholder", "ROLE_USER"));
		mockMvc.perform(get("/admin/users/" + target.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("edit-target")))
				// 编辑页提交地址应带 id，而不是落到新增接口
				.andExpect(content().string(containsString("/admin/users/" + target.getId())));
	}
}
