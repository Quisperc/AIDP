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

	@PostMapping("/api/sso-logout")
	public String ssoLogout(@RequestParam("username") String username) {
		System.out.println("Received SSO logout request for user: " + username);

		List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
		for (Object principal : allPrincipals) {
			if (principal instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser) {
				org.springframework.security.oauth2.core.oidc.user.OidcUser user = (org.springframework.security.oauth2.core.oidc.user.OidcUser) principal;
				if (user.getName().equals(username) || user.getPreferredUsername().equals(username)) {
					expireUserSessions(principal);
				}
			} else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
				org.springframework.security.core.userdetails.UserDetails user = (org.springframework.security.core.userdetails.UserDetails) principal;
				if (user.getUsername().equals(username)) {
					expireUserSessions(principal);
				}
			} else if (principal.toString().equals(username)) {
				expireUserSessions(principal);
			}
		}

		return "Logged out";
	}

	private void expireUserSessions(Object principal) {
		List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
		for (SessionInformation session : sessions) {
			session.expireNow();
			System.out.println("Invalidated session: " + session.getSessionId());
		}
	}
}
