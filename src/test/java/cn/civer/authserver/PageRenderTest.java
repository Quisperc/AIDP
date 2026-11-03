package cn.civer.authserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 匿名页渲染回归。
 *
 * 页脚（备案号 / 图标）来自 FooterModelAdvice + fragments/footer.html。
 * 这条链路原本没有任何测试：字段名写错不会报错，只会渲染成空白，
 * 而备案信息缺失在国内属于合规问题。这里从 HTTP 层断言它真的出现了。
 *
 * 注意 properties 显式给出：测试 profile（application-test.yml）不再覆盖
 * app.footer.*，但主配置里的真实备案号不适合被断言（换号就要改测试），
 * 这里用一组测试值验证「配置 → 片段 → 页面」这条链路本身。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
		"app.footer.icp=渝ICP备0000000号",
		"app.footer.icp-url=https://beian.miit.gov.cn/",
		// 刻意写成 /static/images/... 验证「去掉 /static 前缀」的规范化逻辑
		"app.footer.icp-icon=/static/images/icp.png",
		"app.footer.police=渝公网安备50019002505695号",
		"app.footer.police-url=https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=50019002505695"
})
class PageRenderTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.webAppContextSetup(context)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	@DisplayName("登录页渲染备案号与图标，且 /static 前缀被规范化掉")
	void loginPageRendersFooter() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("渝ICP备0000000号")))
				.andExpect(content().string(containsString("渝公网安备50019002505695号")))
				.andExpect(content().string(containsString("src=\"/images/icp.png\"")));
	}

	@Test
	@DisplayName("错误页同样渲染页脚，且不回显脚本协议的回跳地址")
	void errorPageRendersFooter() throws Exception {
		mockMvc.perform(get("/error").param("continue", "javascript:alert(1)"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("渝ICP备0000000号")))
				.andExpect(content().string(not(containsString("javascript:alert(1)"))));
	}
}
