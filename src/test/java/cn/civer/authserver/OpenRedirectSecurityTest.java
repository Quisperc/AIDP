package cn.civer.authserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cn.civer.authserver.service.SsoLogoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private SsoLogoutService ssoLogoutService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        // 用例自己准备一个客户端：登出白名单来自已注册客户端的 redirect_uris，
        // 依赖启动预置数据会让这个测试和初始化逻辑耦合在一起。
        if (registeredClientRepository.findByClientId("test-client") == null) {
            registeredClientRepository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("test-client")
                    .clientSecret("{noop}test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost:8081/login/oauth2/code/oidc-client")
                    .postLogoutRedirectUri("http://localhost:8081/login")
                    .scope("openid")
                    .build());
        }
        // 白名单有缓存，建完客户端必须失效，否则本类里先跑的用例读到的是空集合
        ssoLogoutService.invalidateHostCache();
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

    /**
     * 错误页的「返回」按钮同样不能用请求参数直接拼 href：
     * 认证中心域名握着 SSO 会话，一个 javascript: 链接就在那里执行脚本。
     */
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
