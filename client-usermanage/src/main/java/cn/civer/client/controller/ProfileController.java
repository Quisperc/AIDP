package cn.civer.client.controller;

import cn.civer.client.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

/**
 * 个人资料 REST 接口。
 *
 * 原先改完账号后由服务端拼 URL 并重定向到认证中心做全局登出；前后端分离后
 * 该跳转交给前端（改完后自己跳 /oauth2/logout-all），后端只负责改数据。
 */
@RestController
@RequestMapping("/admin/profile")
public class ProfileController {

	private final UserService userService;

	public ProfileController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public UserService.UserDto me(@AuthenticationPrincipal OidcUser principal) {
		UserService.UserDto dto = new UserService.UserDto();
		dto.setUsername(principal != null ? principal.getName() : null);
		dto.setPassword(null);
		return dto;
	}

	/**
	 * 修改自己的账号。至少填写新用户名或新密码之一。
	 * 成功后调用方应主动触发全局登出，否则旧会话仍以旧用户名继续有效。
	 */
	@PutMapping
	public ResponseEntity<Void> updateSelf(@RequestBody UserService.UserDto user) {
		boolean hasUsername = user.getUsername() != null && !user.getUsername().isBlank();
		boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
		if (!hasUsername && !hasPassword) {
			return ResponseEntity.badRequest().build();
		}
		userService.updateCurrentUserProfile(user);
		return ResponseEntity.ok().build();
	}
}
