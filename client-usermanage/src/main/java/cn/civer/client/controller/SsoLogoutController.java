package cn.civer.client.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * OIDC Back-Channel Logout：接收认证中心推送的 logout_token，失效本应用内该用户的会话。
 *
 * 精简与修复：
 * - 原先遍历 SessionRegistry.getAllPrincipals()，但 Redis 模式用的
 *   SpringSessionBackedSessionRegistry 明确不支持该方法，登出广播一直被拒。
 *   改成用 FindByIndexNameSessionRepository 按 principal 索引直接查，精确且不依赖遍历。
 * - JwtDecoder 构造一次即可（内部缓存 JWKS），去掉双重检查锁。
 * - 不再手工解析未验签 JWT 的 payload 去猜 issuer，直接用配置里的 app.auth-server-url。
 */
@RestController
public class SsoLogoutController {

	private static final Logger log = LoggerFactory.getLogger(SsoLogoutController.class);

	private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> indexedSessions;
	private final SessionRegistry sessionRegistry;
	private final JwtDecoder jwtDecoder;
	private final String clientId;
	private final String expectedIssuer;

	public SsoLogoutController(
			ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> indexedSessions,
			SessionRegistry sessionRegistry,
			@Value("${app.auth-server-url}") String issuerUri,
			@Value("${spring.security.oauth2.client.registration.oidc-client.client-id}") String clientId) {
		this.indexedSessions = indexedSessions;
		this.sessionRegistry = sessionRegistry;
		String base = issuerUri.replaceAll("/$", "");
		this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(base + "/oauth2/jwks").build();
		this.clientId = clientId;
		this.expectedIssuer = base;
	}

	@PostMapping("/api/sso-logout")
	public String ssoLogout(@RequestParam(name = "logout_token", required = false) String logoutToken) {
		if (logoutToken == null) {
			return "Missing logout_token";
		}
		try {
			Jwt jwt = jwtDecoder.decode(logoutToken);
			if (!jwt.getAudience().contains(clientId)) {
				throw new IllegalStateException("Invalid audience");
			}
			if (!expectedIssuer.equals(String.valueOf(jwt.getIssuer()).replaceAll("/$", ""))) {
				throw new IllegalStateException("Invalid issuer");
			}
			String username = jwt.getSubject();
			int count = expireUserSessions(username);
			log.info("[sso-logout] invalidated {} session(s) for user={}", count, username);
			return "Logged out";
		} catch (Exception e) {
			log.warn("[sso-logout] rejected: {}", e.getMessage());
			return "Bad Request";
		}
	}

	/** Redis 模式：按 principal 索引精确查；单机模式：回退到内存注册表 */
	private int expireUserSessions(String username) {
		FindByIndexNameSessionRepository<? extends Session> repository = indexedSessions.getIfAvailable();
		if (repository != null) {
			Map<String, ? extends Session> sessions = repository.findByIndexNameAndIndexValue(
					FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
			sessions.values().forEach(session -> repository.deleteById(session.getId()));
			return sessions.size();
		}
		int count = 0;
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			if (username.equals(principalName(principal))) {
				List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
				sessions.forEach(SessionInformation::expireNow);
				count += sessions.size();
			}
		}
		return count;
	}

	private static String principalName(Object principal) {
		if (principal instanceof UserDetails user) {
			return user.getUsername();
		}
		if (principal instanceof OidcUser user) {
			return user.getName();
		}
		return principal.toString();
	}
}
