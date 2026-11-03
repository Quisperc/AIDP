package cn.civer.client.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OIDC Back-Channel Logout 端点：接收认证中心推送的 logout_token，失效本应用内该用户的会话。
 *
 * 本模块是接入模板，逻辑保持最小化以便复制改造；生产系统可参考
 * client-usermanage 中更完整的实现（含 iss 尾斜杠容错）。
 */
@RestController
public class SsoLogoutController {

	private static final Logger log = LoggerFactory.getLogger(SsoLogoutController.class);

	private final SessionRegistry sessionRegistry;

	@Value("${spring.security.oauth2.client.provider.auth-server.issuer-uri}")
	private String issuerUri;

	@Value("${spring.security.oauth2.client.registration.oidc-client.client-id}")
	private String clientId;

	public SsoLogoutController(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	/** 复用 decoder：每次 new 会丢弃内部的 JWKS 缓存，导致每次登出都重新拉取密钥集 */
	private volatile JwtDecoder cachedDecoder;
	private volatile String cachedDecoderUri;

	@PostMapping("/api/sso-logout")
	public String ssoLogout(@RequestParam(name = "logout_token", required = false) String logoutToken) {
		if (logoutToken == null) {
			return "Missing logout_token";
		}

		try {
			// 1. Initialize Decoder
			JwtDecoder decoder = decoder(issuerUri + "/oauth2/jwks");

			// 2. Decode & Verify
			Jwt jwt = decoder.decode(logoutToken);

			// 3. Validate Claims
			if (!jwt.getAudience().contains(clientId)) {
				throw new IllegalStateException("Invalid audience: " + jwt.getAudience());
			}
			if (!jwt.getIssuer().toString().equals(issuerUri)) {
				throw new IllegalStateException("Invalid issuer: " + jwt.getIssuer());
			}

			// 4. Extract Subject
			String username = jwt.getSubject();
			log.info("[sso-logout] back-channel logout received: user={}", username);

			// 5. Invalidate Session
			expireUserSessions(username);

			return "Logged out";

		} catch (Exception e) {
			log.warn("[sso-logout] back-channel logout rejected: {}", e.getMessage());
			return "Bad Request";
		}
	}

	private JwtDecoder decoder(String jwkSetUri) {
		JwtDecoder current = cachedDecoder;
		if (current != null && jwkSetUri.equals(cachedDecoderUri)) {
			return current;
		}
		synchronized (this) {
			if (cachedDecoder != null && jwkSetUri.equals(cachedDecoderUri)) {
				return cachedDecoder;
			}
			JwtDecoder created = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
			cachedDecoder = created;
			cachedDecoderUri = jwkSetUri;
			return created;
		}
	}

	private void expireUserSessions(String username) {
		List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
		for (Object principal : allPrincipals) {
			String principalName = null;
			if (principal instanceof UserDetails userDetails) {
				principalName = userDetails.getUsername();
			} else if (principal instanceof OidcUser oidcUser) {
				principalName = oidcUser.getName();
			} else {
				principalName = principal.toString();
			}

			if (username.equals(principalName)) {
				List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
				for (SessionInformation session : sessions) {
					session.expireNow();
				}
				log.info("[sso-logout] invalidated {} session(s) for user={}", sessions.size(), username);
			}
		}
	}
}
