package cn.civer.authserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * 开放重定向回归测试。
 *
 * 表单登出不再接受请求里的 redirect_uri（Spring Security 没有这个标准参数），
 * 一律固定回到 /login?logout；需要回跳业务系统时走标准的 /connect/logout +
 * 已注册的 post_logout_redirect_uri，由框架做精确匹配。这里从外部证明
 * 「无论传什么 redirect_uri 都不会被当成跳转目标」——不再需要自研 host 白名单。
 *
 * 错误页的 continue 参数同样不能拼进 href：认证中心域名握着 SSO 会话，
 * 一个 javascript: 链接就在那里执行脚本。
 */
@ActiveProfiles("test")
@SpringBootTest
class OpenRedirectSecurityTest {

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
    @DisplayName("表单登出忽略外站 redirect_uri，固定回到登录页")
    @WithMockUser(username = "user", authorities = {"ROLE_USER"})
    void logoutIgnoresExternalRedirect() throws Exception {
        for (String unsafe : new String[]{"https://evil.com/steal", "//evil.com/steal"}) {
            mockMvc.perform(post("/logout").with(csrf()).param("redirect_uri", unsafe))
                    .andExpect(redirectedUrl("/login?logout"));
        }
    }

    @Test
    @DisplayName("表单登出也不接受站内或已注册 host 的 redirect_uri")
    @WithMockUser(username = "user", authorities = {"ROLE_USER"})
    void logoutIgnoresEvenInternalRedirect() throws Exception {
        mockMvc.perform(post("/logout").with(csrf())
                        .param("redirect_uri", "http://localhost:8081/home"))
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @DisplayName("错误页不渲染脚本协议或外站回跳地址")
    void errorPageRejectsUnsafeContinueUrl() throws Exception {
        for (String unsafe : new String[]{"javascript:alert(document.cookie)", "https://evil.com/x", "//evil.com/x"}) {
            mockMvc.perform(get("/error").param("continue", unsafe))
                    .andExpect(content().string(not(containsString(unsafe))));
        }
    }

    @Test
    @DisplayName("错误页允许站内相对路径作为回跳地址")
    void errorPageAllowsInternalContinueUrl() throws Exception {
        mockMvc.perform(get("/error").param("continue", "/users"))
                .andExpect(content().string(containsString("href=\"/users\"")));
    }
}
