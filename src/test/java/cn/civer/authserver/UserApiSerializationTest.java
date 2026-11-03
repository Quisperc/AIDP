package cn.civer.authserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户接口的序列化行为回归测试。
 *
 * 两个必须同时成立、却又互相冲突的要求：
 * 1. 请求体里的 password 必须能反序列化进来，否则创建用户时拿到 null；
 * 2. 响应体里绝不能出现 password。
 *
 * User.password 用的是 @JsonProperty(access = WRITE_ONLY)（只写）。
 * 若误改成 @JsonIgnore，反序列化也会被忽略 —— 第 1 条会静默失效，
 * 而且编译和运行都不报错，只有真去创建用户才会发现。此测试即为拦截它而设。
 */
@SpringBootTest
class UserApiSerializationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders
                    .webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    @Test
    @Transactional
    @DisplayName("创建用户时请求体中的 password 能被正常接收")
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_openid"})
    void createUserReadsPasswordFromRequestBody() throws Exception {
        mvc().perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"json-check-user\",\"password\":\"pwd-123\",\"role\":\"ROLE_USER\"}"))
                .andExpect(status().isOk())
                // 能建成功就说明 password 被读到了（否则编码 null 会失败）
                .andExpect(jsonPath("$.username").value("json-check-user"));
    }

    @Test
    @Transactional
    @DisplayName("响应体中绝不出现 password 字段")
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_openid"})
    void responseNeverExposesPassword() throws Exception {
        mvc().perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-leak-user\",\"password\":\"pwd-123\",\"role\":\"ROLE_USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc().perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @Transactional
    @DisplayName("用户名重复时返回 409 而不是 500")
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_openid"})
    void duplicateUsernameReturnsConflict() throws Exception {
        String body = "{\"username\":\"dup-user\",\"password\":\"pwd-123\",\"role\":\"ROLE_USER\"}";
        mvc().perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc().perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
