package cn.civer.client.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller; // Change to @Controller
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody; // For JSON endpoints

import java.util.Map;

@Controller // Use MVC Controller
public class HomeController {

	@GetMapping("/")
	public String home(Model model, @AuthenticationPrincipal OidcUser principal) {
		if (principal != null) {
			model.addAttribute("username", principal.getName());
			model.addAttribute("idToken", principal.getIdToken().getTokenValue());
		}
		return "index"; // Returns index.html template
	}

	@GetMapping("/admin/dashboard")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	@ResponseBody // Keep this a simple API/JSON response for now
	public Map<String, Object> adminDashboard() {
		return Map.of("message", "Welcome Admin! You have access to this protected resource.");
	}
}
