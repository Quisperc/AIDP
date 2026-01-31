package cn.civer.authserver.controller;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserManagementController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public List<User> getUsers() {
		return userRepository.findAll();
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<User> createUser(@RequestBody User user) {
		user.setPassword(passwordEncoder.encode(user.getPassword()));
		// Default role if not provided, though typically managed via UI
		if (user.getRole() == null || user.getRole().isEmpty()) {
			user.setRole("ROLE_USER");
		}
		user.setEnabled(true); // Default enabled
		User saved = userRepository.save(user);
		return ResponseEntity.ok(saved);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
		return userRepository.findById(id).map(user -> {
			user.setRole(updatedUser.getRole());
			user.setEnabled(updatedUser.isEnabled());
			if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
				user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
			}
			return ResponseEntity.ok(userRepository.save(user));
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		return userRepository.findById(id).map(user -> {
			user.setEnabled(false); // Soft delete
			userRepository.save(user);
			return ResponseEntity.ok().<Void>build();
		}).orElse(ResponseEntity.notFound().build());
	}
}
