package cn.civer.authserver;

import cn.civer.authserver.dto.ClientDto;
import cn.civer.authserver.dto.ClientSummaryDto;
import cn.civer.authserver.service.ClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端注册/编辑的字段归一化回归。
 *
 * 守两件事：
 * 1. 表单里「显示名称」是可选的，而 client_name 列 NOT NULL —— 留空必须兜底成 client_id，
 *    否则会以 500 结束。
 * 2. post-logout 地址留空时不能写入字符串 "null"，编辑时也不能把原有的多条压成一条。
 */
@ActiveProfiles("test")
@SpringBootTest
class ClientRegistrationTest {

	@Autowired
	private ClientService clientService;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	@Test
	@Transactional
	@DisplayName("显示名称与 post-logout 地址留空时按空值处理，不写入 \"null\"")
	void blankOptionalFieldsAreNormalized() {
		clientService.register(new ClientDto("test-blank-name", "secret",
				"https://app.example.com/login/oauth2/code/oidc-client", null, null, true));

		ClientSummaryDto saved = clientService.getClient("test-blank-name");
		assertEquals("test-blank-name", saved.clientName(), "显示名称留空应兜底为 client_id");
		assertNull(saved.postLogoutRedirectUri(), "未填写时不应产生占位值");
	}

	@Test
	@Transactional
	@DisplayName("编辑客户端时未填 post-logout 地址，原有全部保留")
	void updateKeepsAllPostLogoutUris() {
		registeredClientRepository.save(RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("test-multi-logout")
				.clientSecret("{noop}secret")
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("https://app.example.com/cb")
				.postLogoutRedirectUri("https://app.example.com/logout-a")
				.postLogoutRedirectUri("https://app.example.com/logout-b")
				.scope("openid")
				.build());

		clientService.update("test-multi-logout",
				new ClientDto(null, null, "https://app.example.com/cb", null, "改个名字", null));

		RegisteredClient updated = registeredClientRepository.findByClientId("test-multi-logout");
		assertEquals("改个名字", updated.getClientName());
		assertEquals(Set.of("https://app.example.com/logout-a", "https://app.example.com/logout-b"),
				updated.getPostLogoutRedirectUris(), "未提交新地址时应保留原有的多条记录");
		assertEquals(Set.of("https://app.example.com/cb"), updated.getRedirectUris());
	}

	@Test
	@Transactional
	@DisplayName("编辑时未提交 PKCE 开关沿用原值，只有显式 false 才关闭")
	void updateKeepsPkceUnlessExplicitlyChanged() {
		clientService.register(new ClientDto("pkce-guard", "secret",
				"https://app.example.com/cb", null, null, true));
		assertTrue(registeredClientRepository.findByClientId("pkce-guard")
				.getClientSettings().isRequireProofKey(), "新建客户端默认强制 PKCE");

		// null：非表单调用方没给这个字段，沿用原值，不能被静默改成 false
		clientService.update("pkce-guard", new ClientDto("pkce-guard", null,
				"https://app.example.com/cb", null, null, null));
		assertTrue(registeredClientRepository.findByClientId("pkce-guard")
				.getClientSettings().isRequireProofKey(), "沿用原值，不应静默关闭");

		// 显式 false：表单里明确选择关闭才生效
		clientService.update("pkce-guard", new ClientDto("pkce-guard", null,
				"https://app.example.com/cb", null, null, false));
		assertFalse(registeredClientRepository.findByClientId("pkce-guard")
				.getClientSettings().isRequireProofKey(), "显式 false 应能关闭");
	}
}
