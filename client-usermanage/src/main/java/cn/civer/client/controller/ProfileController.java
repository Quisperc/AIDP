package cn.civer.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import feign.FeignException;
import cn.civer.client.service.UserService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

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
			model.addAttribute("user", user);
			model.addAttribute("principal", org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal());
			model.addAttribute("error", "请填写新用户名或新密码。");
			return "profile";
		}
		// 修改密码时需确认一致
		if (hasPassword && !user.getPassword().equals(confirmPassword)) {
			model.addAttribute("user", user);
			model.addAttribute("principal", org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal());
			model.addAttribute("error", "两次输入的新密码不一致。");
			return "profile";
		}
		try {
			userService.updateCurrentUserProfile(user);
		} catch (FeignException e) {
			String message = e.status() == 409 ? "该用户名已被使用，请换一个。" : "修改失败，请稍后重试。";
			model.addAttribute("user", user);
			model.addAttribute("principal", org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal());
			model.addAttribute("error", message);
			return "profile";
		} catch (Exception e) {
			model.addAttribute("user", user);
			model.addAttribute("principal", org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal());
			model.addAttribute("error", "修改失败，请稍后重试。");
			return "profile";
		}
		// 修改成功：先清本端会话再跳 SSO 登出，避免回跳时仍带旧会话；无竞态、不依赖 query 参数，更宜生产
		if (request.getSession(false) != null) {
			request.getSession().invalidate();
		}
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		String returnTo = (baseUrl == null || baseUrl.isEmpty() ? "" : baseUrl.replaceAll("/$", "")) + "/login";
		String logoutUrl = (authServerUrl == null ? "" : authServerUrl.replaceAll("/$", "")) + "/logout?redirect_uri="
				+ java.net.URLEncoder.encode(returnTo, java.nio.charset.StandardCharsets.UTF_8);
		return "redirect:" + logoutUrl;
	}
}
