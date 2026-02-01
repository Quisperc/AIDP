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

	public SsoLogoutController(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	/**
	 * Receive broadcast logout notification from Auth Server.
	 * Invalidates all sessions for the given username.
	 */
	@org.springframework.beans.factory.annotation.Value("${app.sso-secret}")
	private String expectedSecret;

	/**
	 * Receive broadcast logout notification from Auth Server.
	 * Invalidates all sessions for the given username.
	 */
	@PostMapping("/api/sso-logout")
	public String ssoLogout(@RequestParam("username") String username,
			@org.springframework.web.bind.annotation.RequestHeader(value = "X-SSO-Secret", required = false) String secret) {

		if (secret == null || !secret.equals(expectedSecret)) {
			System.err.println("SSO Logout Rejected: Invalid Secret for user " + username);
			return "Forbidden";
		}

		System.out.println("Received SSO logout request for user: " + username);

		List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
		for (Object principal : allPrincipals) {
			if (isUser(principal, username)) {
				expireUserSessions(principal);
			}
		}

		return "Logged out";
	}

	private boolean isUser(Object principal, String username) {
		if (principal instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser) {
			org.springframework.security.oauth2.core.oidc.user.OidcUser user = (org.springframework.security.oauth2.core.oidc.user.OidcUser) principal;
			return user.getName().equals(username) || user.getPreferredUsername().equals(username);
		} else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
			return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername()
					.equals(username);
		} else {
			return principal.toString().equals(username);
		}
	}

	private void expireUserSessions(Object principal) {
		List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
		for (SessionInformation session : sessions) {
			session.expireNow();
			System.out.println("Invalidated session: " + session.getSessionId());
		}
	}
}
