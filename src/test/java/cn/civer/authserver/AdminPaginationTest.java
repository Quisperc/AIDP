package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import cn.civer.authserver.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理台列表分页。
 *
 * 断言只盯「一页最多 20 条」「翻页能拿到下一页」「越界回到最后一页」，
 * 不对总数做精确断言——同一份 H2 库被所有用例共享，精确总数会互相干扰。
 */
@ActiveProfiles("test")
@SpringBootTest
class AdminPaginationTest {

	private static final int SIZE = 20;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserAdminService userAdminService;

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
	@DisplayName("用户列表：第一页最多 20 条，翻页可取下一页，越界回到最后一页")
	@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
	void userListIsPaged() throws Exception {
		for (int i = 0; i < 25; i++) {
			userRepository.save(new User("pager-user-" + i, passwordEncoder.encode("pwd-123"), "ROLE_USER"));
		}

		var firstPage = userAdminService.listUsers(0, SIZE);
		assertThat(firstPage.items()).hasSize(SIZE);
		assertThat(firstPage.totalElements()).isGreaterThanOrEqualTo(25);
		assertThat(firstPage.hasNext()).isTrue();

		var secondPage = userAdminService.listUsers(1, SIZE);
		assertThat(secondPage.items()).isNotEmpty();
		assertThat(secondPage.page()).isEqualTo(1);

		// 越界页码应回到最后一页，而不是返回空表（用户翻着页数据被删掉时的常见情形）
		var beyond = userAdminService.listUsers(999, SIZE);
		assertThat(beyond.items()).isNotEmpty();
		assertThat(beyond.page()).isEqualTo(beyond.totalPages() - 1);

		// 第一页有「下一页」，第二页有「上一页」——两个断言分开，避免受「共有多少页」影响
		mockMvc.perform(get("/admin/users"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("下一页")))
				.andExpect(content().string(containsString("共 ")));

		mockMvc.perform(get("/admin/users").param("page", "1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("上一页")));
	}
}
