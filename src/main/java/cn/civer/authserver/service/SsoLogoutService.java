package cn.civer.authserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 全局退出：吊销当前用户在认证中心的全部授权同意与已签发令牌。
 *
 * 只做服务端令牌生命周期管理，不实现任何私有协议扩展：
 * - 认证中心侧的会话终止由 Spring Security 的 logout 过滤器完成；
 * - 各子系统的本地会话由各应用自行按 OIDC RP-Initiated Logout
 *   （discovery 的 end_session_endpoint，即 /connect/logout）结束，
 *   或在 access token 失效后自然退出。IdP 不向子系统推送私有登出消息。
 *
 * 两处删除缺一不可：consent 决定下次是否重弹同意页，authorization 决定
 * access / refresh / id token 是否还认。只删 consent 时客户端手里的
 * refresh_token 仍能在有效期内静默续期，「已退出」只是不再弹同意页。
 */
@Service
public class SsoLogoutService {

	private static final Logger log = LoggerFactory.getLogger(SsoLogoutService.class);

	private final JdbcTemplate jdbcTemplate;

	public SsoLogoutService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** @param principalName 当前登录名，与 oauth2_authorization_consent.principal_name 一致 */
	@Transactional
	public void performGlobalLogout(String principalName) {
		if (!StringUtils.hasText(principalName)) {
			return;
		}
		int consents = jdbcTemplate.update(
				"DELETE FROM oauth2_authorization_consent WHERE principal_name = ?", principalName);
		int authorizations = jdbcTemplate.update(
				"DELETE FROM oauth2_authorization WHERE principal_name = ?", principalName);
		log.info("[sso-logout] revoked principal={}: consents={}, authorizations={}",
				principalName, consents, authorizations);
	}
}
