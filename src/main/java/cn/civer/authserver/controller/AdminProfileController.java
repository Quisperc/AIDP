package cn.civer.authserver.controller;

import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.service.AuditService;
import cn.civer.authserver.service.UserAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
 * 改用户名或密码后会话里的身份就过期了，因此保存成功后直接走全局退出：
 * 清本机会话 + 广播各子系统，让用户用新凭据重新登录。否则旧用户名在
 * 别的系统里仍然是「有效登录态」，属于权限残留。
 */
@Controller
@RequestMapping("/admin/profile")
public class AdminProfileController {

	private final UserAdminService userAdminService;
	private final AuditService auditService;

	public AdminProfileController(UserAdminService userAdminService, AuditService auditService) {
		this.userAdminService = userAdminService;
		this.auditService = auditService;
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
		// 凭据已变，旧会话必须作废：清本地 + 广播各客户端，回登录页
		return "redirect:/oauth2/logout-all";
	}
}
