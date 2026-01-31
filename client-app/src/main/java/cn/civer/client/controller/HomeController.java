package cn.civer.client.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HomeController {

	@GetMapping("/")
	public Map<String, Object> home(@AuthenticationPrincipal OidcUser principal) {
		Map<String, Object> model = new HashMap<>();
		if (principal != null) {
			model.put("username", principal.getName());
			model.put("attributes", principal.getAttributes());
			model.put("authorities", principal.getAuthorities());
		} else {
			model.put("message", "Not logged in");
		}
		return model;
	}

	@GetMapping("/admin/dashboard")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')") // or check claims manually if authority mapping issues
	public Map<String, Object> adminDashboard() {
		return Map.of("message", "Welcome Admin!");
	}
}
