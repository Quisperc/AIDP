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
@RequestMapping("/users")
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
		return "redirect:/users";
	}

	@PostMapping("/{id}/delete") // Using POST for form submission simplicity instead of DELETE
	public String deleteUser(@org.springframework.web.bind.annotation.PathVariable Long id) {
		userService.deleteUser(id);
		return "redirect:/users";
	}

	@PostMapping("/{id}/enable")
	public String enableUser(@org.springframework.web.bind.annotation.PathVariable Long id) {
		// Re-using update logic. We need to fetch generic first or just patch.
		// For simplicity, client side can pass full object or we just patch enabled
		// status.
		// Wait, UserService.updateUser replaces fields.
		// Let's implement toggle in service or here.
		// Actually, since we need to read before write to be safe, or API supports
		// patch.
		// API supports PUT. I will assuming fetching list has data.
		// To keep it simple, I'll add specific disable/enable flag in next iteration if
		// needed.
		// For now, let's stick to "Delete" button doing the soft delete.
		// User requested "Enable/Disable".
		// I'll add a helper to fetch single user or just pass the DTO from form?
		// Let's just implement disable via delete endpoints as per plan.
		// For enable, we need a way.
		// Let's add specific logic to toggle.
		return "redirect:/users";
	}

	@GetMapping("/{id}/edit")
	public String editUserPage(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
		// ideally fetch user by id, but our service returns list.
		// For simplicity in this demo without specific 'findById' in service (yet),
		// we can filter from list or add findById to service.
		// Let's add findById to service for better practice or just loop list since
		// it's small.
		// Adding findById to service is better.
		UserService.UserDto user = userService.getUsers().stream()
				.filter(u -> u.getId().equals(id))
				.findFirst()
				.orElse(new UserService.UserDto());
		model.addAttribute("user", user);
		return "user-edit";
	}

	@PostMapping("/{id}/update")
	public String updateUser(@org.springframework.web.bind.annotation.PathVariable Long id,
			@ModelAttribute UserService.UserDto user) {
		// user object from form contains updated fields.
		userService.updateUser(id, user);
		return "redirect:/users";
	}

	@PostMapping("/{id}/status")
	public String toggleStatus(@org.springframework.web.bind.annotation.PathVariable Long id, boolean enabled,
			String username, String role) {
		UserService.UserDto dto = new UserService.UserDto();
		dto.setEnabled(enabled);
		dto.setUsername(username);
		dto.setRole(role);
		userService.updateUser(id, dto);
		return "redirect:/users";
	}
}
