package cn.civer.client.controller;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
			// 1. Initialize Decoder
			org.springframework.security.oauth2.jwt.JwtDecoder decoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder
					.withJwkSetUri(issuerUri + "/oauth2/jwks")
					.build();

			// 2. Decode & Verify
			org.springframework.security.oauth2.jwt.Jwt jwt = decoder.decode(logoutToken);

			// 3. Validate Claims
			if (!jwt.getAudience().contains(clientId)) {
				throw new IllegalStateException("Invalid audience: " + jwt.getAudience());
			}
			if (!jwt.getIssuer().toString().equals(issuerUri)) {
				throw new IllegalStateException("Invalid issuer: " + jwt.getIssuer());
			}

			// 4. Extract Subject
			String username = jwt.getSubject();
			System.out.println("OIDC Logout received for user: " + username);

			// 5. Invalidate Session
			expireUserSessions(username);

			return "Logged out";

		} catch (Exception e) {
			System.err.println("OIDC Logout Failed: " + e.getMessage());
			return "Bad Request";
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
