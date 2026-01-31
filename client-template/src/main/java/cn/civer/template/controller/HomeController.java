package cn.civer.template.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	@GetMapping("/")
	public String index(Model model, @AuthenticationPrincipal OidcUser principal) {
		if (principal != null) {
			model.addAttribute("username", principal.getPreferredUsername());
			model.addAttribute("email", principal.getEmail());
			model.addAttribute("attributes", principal.getAttributes());
			model.addAttribute("authorities", principal.getAuthorities());
		}
		return "index";
	}
}
