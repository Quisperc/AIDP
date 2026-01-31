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
}
