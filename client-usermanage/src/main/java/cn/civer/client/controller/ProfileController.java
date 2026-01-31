package cn.civer.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import cn.civer.client.service.UserService;

@Controller
public class ProfileController {

	private final UserService userService;

	public ProfileController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/profile")
	public String profile(Model model, @AuthenticationPrincipal OidcUser principal) {
		if (principal != null) {
			UserService.UserDto user = new UserService.UserDto();
			user.setUsername(principal.getName());
			// We can't easily get other fields like ID or specific DB role unless we query
			// API "me",
			// but principal has most info needed for display.
			// For editing, we mostly care about setting NEW values.
			model.addAttribute("user", user);
			model.addAttribute("principal", principal);
		}
		return "profile";
	}

	@PostMapping("/profile")
	public String updateProfile(@ModelAttribute UserService.UserDto user) {
		userService.updateCurrentUserProfile(user);
		// If username changed, we should logout.
		// For simplicity, we just redirect back to profile.
		// If username changed, next request might fail or be weird until re-login if
		// 'sub' check fails,
		// but OidcUser is from session.
		// Best practice: Logout user to force token refresh with new username.
		return "redirect:/logout";
	}
}
