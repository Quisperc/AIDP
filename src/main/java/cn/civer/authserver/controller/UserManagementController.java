package cn.civer.authserver.controller;

import cn.civer.authserver.dto.UserResponse;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 用户管理接口。
 *
 * 响应一律走 {@link UserResponse}（不含 password 字段）；请求体仍用 User 实体，
 * 其 password 标注为 WRITE_ONLY，可接收但不会回吐。
 */
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
	public List<UserResponse> getUsers() {
		return userRepository.findAll(Sort.by("id")).stream()
				.map(UserResponse::from)
				.toList();
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<UserResponse> createUser(@RequestBody User user) {
		if (user.getUsername() == null || user.getUsername().isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		if (user.getPassword() == null || user.getPassword().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		// 用户名唯一，重复提交应返回 409 而不是让数据库抛约束异常（会变成 500）
		if (userRepository.findByUsername(user.getUsername()).isPresent()) {
			return ResponseEntity.status(409).build();
		}
		user.setPassword(passwordEncoder.encode(user.getPassword()));
		// Default role if not provided, though typically managed via UI
		if (user.getRole() == null || user.getRole().isEmpty()) {
			user.setRole("ROLE_USER");
		}
		user.setEnabled(true); // Default enabled
		return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
		return userRepository.findById(id).map(user -> {
			user.setRole(updatedUser.getRole());
			user.setEnabled(updatedUser.isEnabled());
			if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
				user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
			}
			return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
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

	@PutMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<UserResponse> updateCurrentUser(
			@AuthenticationPrincipal Jwt principal,
			@RequestBody User updatedUser) {
		String username = principal.getSubject();
		return userRepository.findByUsername(username).map(user -> {
			if (updatedUser.getUsername() != null && !updatedUser.getUsername().isEmpty()) {
				String newUsername = updatedUser.getUsername().trim();
				// 若修改用户名，检查是否已被其他用户占用
				Optional<User> existing = userRepository.findByUsername(newUsername);
				if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
					return ResponseEntity.status(409).body((UserResponse) null);
				}
				user.setUsername(newUsername);
			}
			if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
				user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
			}
			User saved = userRepository.save(user);
			// 不在此处广播：由客户端重定向到 /logout 后，由 SsoLogoutSuccessHandler 统一做一次全局退出（清 consent + 广播），避免重复
			return ResponseEntity.ok(UserResponse.from(saved));
		}).orElse(ResponseEntity.notFound().build());
	}
}
