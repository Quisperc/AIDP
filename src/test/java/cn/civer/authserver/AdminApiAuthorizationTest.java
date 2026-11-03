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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理接口越权回归测试。
 *
 * 背景：AuthServerApplication 曾缺少 @EnableMethodSecurity，导致各 Controller 上的
 * {@code @PreAuthorize("hasAuthority('ROLE_ADMIN')")} 静默失效（不报错但不生效）。
 * 而 SecurityConfig 对 /api/** 只要求 SCOPE_openid —— 任何已登录用户都具备该 scope，
 * 于是普通用户即可调用 /api/users、/api/clients 进行增删改查。
 *
 * 因此下面每个用例都显式带上 SCOPE_openid，确保请求能穿过 URL 层，
 * 从而精准检验「方法层权限」是否真的生效：
 * 若 @EnableMethodSecurity 被误删，前两个用例会从 403 变成 200 —— 即漏洞复现。
 *
 * 说明：这里手动装配 MockMvc 而非使用 @AutoConfigureMockMvc，
 * 因为 Spring Boot 4 已将该测试切片拆到独立模块（spring-boot-starter-webmvc-test），
 * 手动装配只需 spring-test，依赖更少、不受镜像同步影响。
 */
@SpringBootTest
class AdminApiAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 必须挂上 springSecurity()，否则请求不会经过 Spring Security 过滤器链
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("普通用户不得访问用户管理接口")
    @WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_openid"})
    void normalUserIsDeniedOnUserApi() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("普通用户不得访问客户端管理接口")
    @WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_openid"})
    void normalUserIsDeniedOnClientApi() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可以访问用户管理接口")
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_openid"})
    void adminIsAllowedOnUserApi() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理员可以访问客户端管理接口")
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_openid"})
    void adminIsAllowedOnClientApi() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk());
    }
}
