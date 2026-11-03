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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * OIDC Back-Channel Logout 端点：接收认证中心推送的 logout_token，失效本应用内该用户的会话。
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
			// 用 token 里的 iss 拼 JWK URL（仅当与配置 issuer 一致时），避免 tidp 实际 issuer 与客户端配置略有差异导致验签失败
			String issFromToken = getIssuerFromTokenPayload(logoutToken);
			String base = issuerUri != null ? issuerUri.replaceAll("/$", "") : "";
			if (issFromToken != null && !issFromToken.isEmpty()
					&& base.equals(issFromToken.replaceAll("/$", ""))) {
				base = issFromToken.replaceAll("/$", "");
			}
			String jwkSetUri = base + "/oauth2/jwks";

			Jwt jwt = decoder(jwkSetUri).decode(logoutToken);

			if (!jwt.getAudience().contains(clientId)) {
				throw new IllegalStateException("Invalid audience: " + jwt.getAudience());
			}
			String expectedIssuer = issuerUri != null ? issuerUri.replaceAll("/$", "") : "";
			if (!expectedIssuer.equals(jwt.getIssuer().toString().replaceAll("/$", ""))) {
				throw new IllegalStateException("Invalid issuer: " + jwt.getIssuer());
			}

			String username = jwt.getSubject();
			log.info("[sso-logout] back-channel logout received: user={}", username);

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

	/** 从未验签的 JWT payload 中解析 iss，仅用于选择 JWK Set URI（且仅当与配置 issuer 一致时使用） */
	private static String getIssuerFromTokenPayload(String token) {
		try {
			int i = token.indexOf('.');
			int j = token.indexOf('.', i + 1);
			if (j <= i) {
				return null;
			}
			String payload = token.substring(i + 1, j);
			byte[] decoded = Base64.getUrlDecoder().decode(payload);
			String json = new String(decoded, StandardCharsets.UTF_8);
			int issIdx = json.indexOf("\"iss\"");
			if (issIdx < 0) {
				return null;
			}
			int colon = json.indexOf(':', issIdx + 1);
			int start = json.indexOf('"', colon + 1) + 1;
			int end = json.indexOf('"', start);
			return start > 0 && end > start ? json.substring(start, end) : null;
		} catch (Exception e) {
			return null;
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
