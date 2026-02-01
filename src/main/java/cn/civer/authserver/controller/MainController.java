package cn.civer.authserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

	@GetMapping("/")
	public String index(org.springframework.security.core.Authentication authentication,
			org.springframework.ui.Model model) {
		if (authentication != null && authentication.isAuthenticated()) {
			model.addAttribute("username", authentication.getName());
			model.addAttribute("roles", authentication.getAuthorities());
			return "login_success";
		}
		return "welcome";
	}

	@GetMapping("/login")
	public String login(org.springframework.security.core.Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()) {
			return "redirect:/";
		}
		return "login";
	}

}
