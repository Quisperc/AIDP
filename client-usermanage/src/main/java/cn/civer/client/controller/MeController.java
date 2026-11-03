package cn.civer.client.controller;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 当前登录者信息。
 *
 * SPA 没有服务端渲染的页面可以注入用户状态，启动时必须有一个接口告诉它
 * 「你是谁、有什么权限」，否则只能靠猜测或把角色塞进首个业务响应里。
 */
@RestController
public class MeController {

	@GetMapping("/api/me")
	public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
		if (principal == null) {
			return MeResponse.anonymous();
		}
		Set<String> roles = principal.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(a -> a.startsWith("ROLE_"))
				.collect(Collectors.toSet());
		return new MeResponse(true, principal.getName(), roles.stream().sorted().toList());
	}

	public record MeResponse(boolean authenticated, String username, List<String> roles) {

		public static MeResponse anonymous() {
			return new MeResponse(false, null, List.of());
		}
	}
}
