package cn.civer.client.controller;

import cn.civer.client.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 当前账号相关接口。
 *
 * 原先拆成 MeController（/api/me）与 ProfileController（/admin/profile）两个类，
 * 职责都是「当前登录者」，合并为一个，减少一层文件与重复注入。
 */
@RestController
public class AccountController {

	private final UserService userService;

	public AccountController(UserService userService) {
		this.userService = userService;
	}

	/** SPA 启动时据此判断登录状态与角色；未认证时由 filter chain 直接返回 401 */
	@GetMapping("/api/me")
	public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
		if (principal == null) {
			return MeResponse.anonymous();
		}
		List<String> roles = principal.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(authority -> authority.startsWith("ROLE_"))
				.sorted()
				.toList();
		return new MeResponse(true, principal.getName(), roles);
	}

	@GetMapping("/admin/profile")
	public UserService.UserDto profile(@AuthenticationPrincipal OidcUser principal) {
		UserService.UserDto dto = new UserService.UserDto();
		dto.setUsername(principal != null ? principal.getName() : null);
		return dto;
	}

	/** 修改成功后由前端主动触发全局登出，否则旧会话仍以旧用户名有效 */
	@PutMapping("/admin/profile")
	public ResponseEntity<Void> updateSelf(@RequestBody UserService.UserDto user) {
		boolean hasUsername = user.getUsername() != null && !user.getUsername().isBlank();
		boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
		if (!hasUsername && !hasPassword) {
			return ResponseEntity.badRequest().build();
		}
		userService.updateCurrentUserProfile(user);
		return ResponseEntity.ok().build();
	}

	public record MeResponse(boolean authenticated, String username, List<String> roles) {

		public static MeResponse anonymous() {
			return new MeResponse(false, null, List.of());
		}
	}
}
