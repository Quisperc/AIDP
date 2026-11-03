package cn.civer.authserver.controller;

import cn.civer.authserver.dto.UserResponse;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.service.UserAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口。
 *
 * 业务逻辑已移到 {@link UserAdminService}；响应一律走 {@link UserResponse}
 * （不含 password 字段）。请求体仍用 User 实体，其 password 为 WRITE_ONLY：
 * 可接收、不回吐。
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

	private final UserAdminService userAdminService;

	public UserManagementController(UserAdminService userAdminService) {
		this.userAdminService = userAdminService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public List<UserResponse> getUsers() {
		return userAdminService.listUsers();
	}

	/** 单个用户：管理端编辑页与「更新前先读最新值」都走这里，避免为了一个 ID 拉全表 */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public UserResponse getUser(@PathVariable Long id) {
		return userAdminService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<UserResponse> createUser(@RequestBody User user) {
		return ResponseEntity.ok(userAdminService.create(user));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
		return ResponseEntity.ok(userAdminService.update(id, updatedUser));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		userAdminService.disable(id);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<UserResponse> updateCurrentUser(
			@AuthenticationPrincipal Jwt principal,
			@RequestBody User updatedUser) {
		return ResponseEntity.ok(userAdminService.updateSelf(principal.getSubject(), updatedUser));
	}
}
