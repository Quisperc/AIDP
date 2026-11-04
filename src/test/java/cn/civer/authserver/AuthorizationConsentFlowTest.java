package cn.civer.authserver;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整授权链路回归：登录 → 授权 → 同意页 → 提交同意 → 回调拿到 code。
 *
 * 这条用例专门守住一个线上事故：Spring Authorization Server 会把
 * java.security.Principal 序列化进 oauth2_authorization.attributes，
 * 提交同意时再反序列化。若登录主体是自定义 UserDetails，Jackson 3 的
 * PolymorphicTypeValidator 会拒绝解析，POST /oauth2/authorize 直接 500。
 * 纯 service 层测试发现不了，必须走一次完整的 filter chain + JDBC 往返。
 */
@ActiveProfiles("test")
@SpringBootTest
class AuthorizationConsentFlowTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	@Transactional
	@DisplayName("登录→授权→提交同意→回调拿到 code，授权记录可正常反序列化")
	void consentFlowCompletes() throws Exception {
		userRepository.save(new User("consent-user", passwordEncoder.encode("pwd-123"), "ROLE_USER"));
		registeredClientRepository.save(RegisteredClient.withId("it-consent-client")
				.clientId("consent-app")
				.clientSecret("{noop}it-consent-secret")
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("https://app.example.com/cb")
				.scope("openid")
				.scope("profile")
				.scope("email")
				.clientSettings(ClientSettings.builder()
						.requireAuthorizationConsent(true)
						.requireProofKey(false)
						.build())
				.build());

		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/login").with(csrf()).session(session)
						.param("username", "consent-user")
						.param("password", "pwd-123"))				.andExpect(status().is3xxRedirection());

		// 授权端点从 queryString 取参数；queryParam() 会生成正确编码的 queryString，
		// 用 .param() 或手工拼 %XX 都会让框架拿到错误的值。
		MvcResult authorizationResult = mockMvc.perform(get("/oauth2/authorize").session(session)
						.queryParam("response_type", "code")
						.queryParam("client_id", "consent-app")
						.queryParam("redirect_uri", "https://app.example.com/cb")
						.queryParam("scope", "openid profile email")
						.queryParam("state", "client-state"))				.andExpect(status().is3xxRedirection())
				.andReturn();

		String consentUrl = authorizationResult.getResponse().getRedirectedUrl();
		assertNotNull(consentUrl);
		assertTrue(consentUrl.contains("/oauth2/consent"), "需要同意时应跳到同意页");

		// URL 里的 state 末尾是 %3D，必须解码后再作为表单值提交
		String rawState = UriComponentsBuilder.fromUriString(consentUrl)
				.build().getQueryParams().getFirst("state");
		assertNotNull(rawState, "同意页必须带上授权请求的 state");
		String state = URLDecoder.decode(rawState, StandardCharsets.UTF_8);

		mockMvc.perform(get(consentUrl).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/oauth2/authorize").with(csrf()).session(session)
						.param("client_id", "consent-app")
						.param("state", state)
						.param("scope", "openid")
						.param("scope", "profile")
						.param("scope", "email"))				.andExpect(status().is3xxRedirection())
				.andExpect(result -> {
					String callback = result.getResponse().getRedirectedUrl();
					assertNotNull(callback);
					assertTrue(callback.startsWith("https://app.example.com/cb?code="),
							"同意后应带着 code 回到回调地址，实际：" + callback);
				});
	}
}
