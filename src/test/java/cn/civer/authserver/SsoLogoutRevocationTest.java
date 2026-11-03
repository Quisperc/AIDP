package cn.civer.authserver;

import cn.civer.authserver.service.SsoLogoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全局登出的吊销范围回归。
 *
 * 背景：早先只删 oauth2_authorization_consent。consent 只决定「下次要不要再弹同意页」，
 * 不决定已发出的令牌还认不认 —— 客户端手里的 refresh_token 仍能在有效期内续期，
 * 于是用户以为「退出全部系统」，实际只是没再弹同意页。现在同时删 oauth2_authorization。
 */
@ActiveProfiles("test")
@SpringBootTest
class SsoLogoutRevocationTest {

	private static final String PRINCIPAL = "revoke-target";
	private static final String BYSTANDER = "keep-target";

	@Autowired
	private SsoLogoutService ssoLogoutService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Transactional
	@DisplayName("全局登出删除该主体的 authorization 与 consent，且不影响其他用户")
	void globalLogoutRevokesAuthorizations() {
		// 清空客户端注册表，避免用例真的向外部地址发 Back-Channel 广播（事务内删除，结束回滚）
		jdbcTemplate.update("DELETE FROM oauth2_registered_client");

		insertAuthorization("auth-revoke", PRINCIPAL);
		insertConsent(PRINCIPAL);
		insertAuthorization("auth-keep", BYSTANDER);

		ssoLogoutService.performGlobalLogout(PRINCIPAL);

		assertEquals(0, countAuthorizations(PRINCIPAL), "该用户的授权记录应被删除（等价于吊销已签发令牌）");
		assertEquals(0, countConsents(PRINCIPAL), "该用户的同意记录应被删除");
		assertEquals(1, countAuthorizations(BYSTANDER), "其他用户的授权记录不应被牵连");
	}

	private void insertAuthorization(String id, String principalName) {
		jdbcTemplate.update("""
				INSERT INTO oauth2_authorization
					(id, registered_client_id, principal_name, authorization_grant_type, authorized_scopes)
				VALUES (?, ?, ?, ?, ?)
				""", id, "it-client", principalName, "authorization_code", "openid");
	}

	private void insertConsent(String principalName) {
		jdbcTemplate.update("""
				INSERT INTO oauth2_authorization_consent (registered_client_id, principal_name, authorities)
				VALUES (?, ?, ?)
				""", "it-client", principalName, "SCOPE_openid");
	}

	private int countAuthorizations(String principalName) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM oauth2_authorization WHERE principal_name = ?",
				Integer.class, principalName);
	}

	private int countConsents(String principalName) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM oauth2_authorization_consent WHERE principal_name = ?",
				Integer.class, principalName);
	}
}
