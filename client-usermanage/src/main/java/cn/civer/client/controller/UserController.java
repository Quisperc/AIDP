package cn.civer.client.controller;

import cn.civer.client.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理 REST 接口（管理后台 SPA 的数据源）。
 *
 * 原先是表单式控制器：用 POST 承载删除、有 /{id}/enable 这种只做重定向的空方法、
 * 返回 Thymeleaf 视图名。改为前后端分离后统一为 REST 语义。
 *
 * 响应里的 password 恒为空：认证中心的 /api/users 现在返回不含密码的 UserResponse，
 * 即使这里把字段置空也是双保险。
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserService.UserDto> listUsers() {
		return userService.getUsers().stream().map(UserController::scrub).toList();
	}

	@GetMapping("/{id}")
	public UserService.UserDto getUser(@PathVariable Long id) {
		return scrub(userService.getUser(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserService.UserDto createUser(@RequestBody UserService.UserDto user) {
		return scrub(userService.createUser(user));
	}

	/**
	 * 更新：以现有记录为基础做局部覆盖。
	 * 密码留空表示不修改；用户名保持不可由管理员直接改动（改自己的账号走 /admin/profile）。
	 */
	@PutMapping("/{id}")
	public UserService.UserDto updateUser(@PathVariable Long id, @RequestBody UserService.UserDto patch) {
		UserService.UserDto existing = userService.getUser(id);
		existing.setRole(patch.getRole() != null ? patch.getRole() : existing.getRole());
		existing.setEnabled(patch.isEnabled());
		if (patch.getPassword() != null && !patch.getPassword().isEmpty()) {
			existing.setPassword(patch.getPassword());
		} else {
			existing.setPassword(null); // 明确不修改密码
		}
		userService.updateUser(id, existing);
		return scrub(existing);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		userService.deleteUser(id);
		return ResponseEntity.noContent().build();
	}

	private static UserService.UserDto scrub(UserService.UserDto user) {
		if (user != null) {
			user.setPassword(null);
		}
		return user;
	}
}
