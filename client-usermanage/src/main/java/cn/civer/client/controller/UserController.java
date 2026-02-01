package cn.civer.client.controller;

import cn.civer.client.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public String listUsers(Model model) {
		model.addAttribute("users", userService.getUsers());
		model.addAttribute("newUser", new UserService.UserDto());
		return "users";
	}

	@PostMapping
	public String createUser(@ModelAttribute UserService.UserDto user) {
		userService.createUser(user);
		return "redirect:/admin/users";
	}

	@PostMapping("/{id}/delete") // Using POST for form submission simplicity instead of DELETE
	public String deleteUser(@org.springframework.web.bind.annotation.PathVariable Long id) {
		userService.deleteUser(id);
		return "redirect:/admin/users";
	}

	@PostMapping("/{id}/enable")
	public String enableUser(@org.springframework.web.bind.annotation.PathVariable Long id) {
		return "redirect:/admin/users";
	}

	@GetMapping("/{id}/edit")
	public String editUserPage(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
		UserService.UserDto user = userService.getUser(id);
		model.addAttribute("user", user);
		return "user-edit";
	}

	@PostMapping("/{id}/update")
	public String updateUser(@org.springframework.web.bind.annotation.PathVariable Long id,
			@ModelAttribute UserService.UserDto user) {
		// user object from form might have null username.
		// logic: existing user details + form updates.
		UserService.UserDto existing = userService.getUser(id);
		existing.setRole(user.getRole());
		// If password is provided, update it. If empty, keep null (Service/Controller
		// handles this).
		existing.setPassword(user.getPassword());
		existing.setEnabled(user.isEnabled());
		// Username is typically immutable or handled via /me, but here admin might
		// update it.
		// user-edit.html has username readonly.
		// For robustness, let's trust the ID.
		userService.updateUser(id, existing);
		return "redirect:/admin/users";
	}

	@PostMapping("/{id}/status")
	public String toggleStatus(@org.springframework.web.bind.annotation.PathVariable Long id) {
		// Robust Logic: Fetch -> Toggle -> Save
		UserService.UserDto user = userService.getUser(id);
		user.setEnabled(!user.isEnabled());
		userService.updateUser(id, user);
		return "redirect:/admin/users";
	}
}
