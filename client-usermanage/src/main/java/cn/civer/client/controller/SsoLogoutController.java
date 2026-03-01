package cn.civer.client.controller;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

@RestController
public class SsoLogoutController {

	private final SessionRegistry sessionRegistry;

	@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.provider.auth-server.issuer-uri}")
	private String issuerUri;

	@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.oidc-client.client-id}")
	private String clientId;

	public SsoLogoutController(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

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

			org.springframework.security.oauth2.jwt.JwtDecoder decoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder
					.withJwkSetUri(jwkSetUri)
					.build();

			org.springframework.security.oauth2.jwt.Jwt jwt = decoder.decode(logoutToken);

			if (!jwt.getAudience().contains(clientId)) {
				throw new IllegalStateException("Invalid audience: " + jwt.getAudience());
			}
			String expectedIssuer = issuerUri != null ? issuerUri.replaceAll("/$", "") : "";
			if (!expectedIssuer.equals(jwt.getIssuer().toString().replaceAll("/$", ""))) {
				throw new IllegalStateException("Invalid issuer: " + jwt.getIssuer());
			}

			String username = jwt.getSubject();
			System.out.println("OIDC Logout received for user: " + username);

			expireUserSessions(username);

			return "Logged out";

		} catch (Exception e) {
			System.err.println("OIDC Logout Failed: " + e.getMessage());
			return "Bad Request";
		}
	}

	/** 从未验签的 JWT payload 中解析 iss，仅用于选择 JWK Set URI（且仅当与配置 issuer 一致时使用） */
	private static String getIssuerFromTokenPayload(String token) {
		try {
			int i = token.indexOf('.');
			int j = token.indexOf('.', i + 1);
			if (j <= i) return null;
			String payload = token.substring(i + 1, j);
			byte[] decoded = Base64.getUrlDecoder().decode(payload);
			String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
			int issIdx = json.indexOf("\"iss\"");
			if (issIdx < 0) return null;
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
			if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
				principalName = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
			} else if (principal instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser) {
				principalName = ((org.springframework.security.oauth2.core.oidc.user.OidcUser) principal).getName();
			} else {
				principalName = principal.toString();
			}

			if (username.equals(principalName)) {
				List<org.springframework.security.core.session.SessionInformation> sessions = sessionRegistry
						.getAllSessions(principal, false);
				for (org.springframework.security.core.session.SessionInformation session : sessions) {
					session.expireNow();
				}
				System.out.println("Invalidated session for user: " + username);
			}
		}
	}
}
