package cn.civer.authserver.controller;

import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.service.AuditService;
import cn.civer.authserver.service.SsoLogoutService;
import cn.civer.authserver.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理台 · 账号自助。
 *
 * 改用户名或密码后会话里的身份就过期了，因此保存成功后立刻做两件事：
 * 1. 服务端吊销该用户在本中心的全部同意与令牌，并向所有子系统广播退出；
 * 2. 清掉本机会话（安全上下文 + session），回到登录页。
 *
 * 两步都在同一个请求内完成，不再像以前那样 302 到一个自研的「全局登出」URL：
 * 浏览器多跳一次既没有额外效果，又要求这个 URL 永久保留，属于给自研端点续命。
 */
@Controller
@RequestMapping("/admin/profile")
public class AdminProfileController {

	private final UserAdminService userAdminService;
	private final AuditService auditService;
	private final SsoLogoutService ssoLogoutService;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AdminProfileController(UserAdminService userAdminService, AuditService auditService,
			SsoLogoutService ssoLogoutService) {
		this.userAdminService = userAdminService;
		this.auditService = auditService;
		this.ssoLogoutService = ssoLogoutService;
	}

	@GetMapping
	public String form(@AuthenticationPrincipal UserDetails principal, Model model) {
		model.addAttribute("active", "profile");
		model.addAttribute("currentUsername", principal != null ? principal.getUsername() : null);
		return "admin/profile";
	}

	@PostMapping
	public String update(@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) String username,
			@RequestParam(required = false) String password,
			@RequestParam(required = false) String confirmPassword,
			HttpServletRequest request, HttpServletResponse response,
			RedirectAttributes ra) {
		String current = principal != null ? principal.getUsername() : null;
		boolean wantsUsername = username != null && !username.isBlank()
				&& !username.trim().equals(current);
		boolean wantsPassword = password != null && !password.isEmpty();
		if (!wantsUsername && !wantsPassword) {
			ra.addFlashAttribute("error", "请至少填写一项：新用户名或新密码。");
			return "redirect:/admin/profile";
		}
		if (wantsPassword && !password.equals(confirmPassword)) {
			ra.addFlashAttribute("error", "两次输入的密码不一致。");
			return "redirect:/admin/profile";
		}
		try {
			userAdminService.updateSelf(current, username, password);
			auditService.record(AuditService.PROFILE_UPDATE, current,
					"usernameChanged=" + wantsUsername + ", passwordChanged=" + wantsPassword);
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/profile";
		}

		// 凭据已变：先吊销令牌与同意（含向各子系统广播），再清本机会话
		ssoLogoutService.performGlobalLogout(current);
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		logoutHandler.logout(request, response, authentication);
		return "redirect:/login?logout";
	}
}
