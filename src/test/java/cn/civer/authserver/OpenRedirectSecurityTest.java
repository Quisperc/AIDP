package cn.civer.authserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * 开放重定向回归测试。
 *
 * /oauth2/revoke-consent 的 redirect_uri 直接取自请求参数。若不做校验，攻击者可构造
 * /oauth2/revoke-consent?redirect_uri=https://evil.com —— 跳转来源仍是本站域名，
 * 可信度极高，是典型的钓鱼手法。
 * 修复后仅允许：站内相对路径，或已注册客户端 redirect_uris 所在 host 的绝对 URL。
 */
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
    @DisplayName("外站绝对 URL 必须被拦截")
    @WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_openid"})
    void externalUrlIsBlocked() throws Exception {
        mockMvc.perform(get("/oauth2/revoke-consent")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "https://evil.com/steal"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("协议相对 URL（//evil.com）必须被拦截")
    @WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_openid"})
    void protocolRelativeUrlIsBlocked() throws Exception {
        mockMvc.perform(get("/oauth2/revoke-consent")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "//evil.com/steal"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("站内相对路径应放行")
    @WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_openid"})
    void internalRelativePathIsAllowed() throws Exception {
        mockMvc.perform(get("/oauth2/revoke-consent")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "/login"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("已注册客户端所在 host 的绝对 URL 应放行")
    @WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_openid"})
    void registeredClientHostIsAllowed() throws Exception {
        // test-client 的 redirect-uris 为 http://localhost:8081/...，故 localhost 在白名单内
        mockMvc.perform(get("/oauth2/revoke-consent")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "http://localhost:8081/home"))
                .andExpect(redirectedUrl("http://localhost:8081/home"));
    }
}
