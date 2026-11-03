package cn.civer.client.controller;

import cn.civer.client.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class ProfileController {

	private final UserService userService;

	@Value("${app.auth-server-url:http://127.0.0.1:8080}")
	private String authServerUrl;
	@Value("${app.base-url:http://127.0.0.1:8081}")
	private String baseUrl;

	public ProfileController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/user/profile")
	public String profile(Model model, @AuthenticationPrincipal OidcUser principal) {
		if (principal != null) {
			UserService.UserDto user = new UserService.UserDto();
			user.setUsername(principal.getName());
			model.addAttribute("user", user);
			model.addAttribute("principal", principal);
		}
		return "profile";
	}

	@PostMapping("/user/profile")
	public String updateProfile(
			@ModelAttribute UserService.UserDto user,
			@RequestParam(name = "confirmPassword", required = false) String confirmPassword,
			Model model,
			RedirectAttributes redirectAttributes,
			HttpServletRequest request) {
		// 至少填写一项
		boolean hasUsername = user.getUsername() != null && !user.getUsername().isBlank();
		boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
		if (!hasUsername && !hasPassword) {
			return renderError(model, user, "请填写新用户名或新密码。");
		}
		// 修改密码时需确认一致
		if (hasPassword && !user.getPassword().equals(confirmPassword)) {
			return renderError(model, user, "两次输入的新密码不一致。");
		}
		try {
			userService.updateCurrentUserProfile(user);
		} catch (RestClientResponseException e) {
			String message = e.getStatusCode().value() == 409 ? "该用户名已被使用，请换一个。" : "修改失败，请稍后重试。";
			return renderError(model, user, message);
		} catch (Exception e) {
			return renderError(model, user, "修改失败，请稍后重试。");
		}
		// 修改成功：先清本端会话再触发 SSO 全局退出（撤销所有客户端授权），避免回跳时仍带旧会话
		if (request.getSession(false) != null) {
			request.getSession().invalidate();
		}
		SecurityContextHolder.clearContext();
		String returnTo = (baseUrl == null || baseUrl.isEmpty() ? "" : baseUrl.replaceAll("/$", "")) + "/login";
		String logoutAllUrl = (authServerUrl == null ? "" : authServerUrl.replaceAll("/$", ""))
				+ "/oauth2/logout-all?redirect_uri="
				+ URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
		return "redirect:" + logoutAllUrl;
	}

	/** 各失败分支的返回内容完全一致，收敛成一处，避免四处重复设置 model */
	private static String renderError(Model model, UserService.UserDto user, String error) {
		model.addAttribute("user", user);
		model.addAttribute("principal", currentPrincipal());
		model.addAttribute("error", error);
		return "profile";
	}

	private static Object currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null ? authentication.getPrincipal() : null;
	}
}
