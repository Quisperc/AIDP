package cn.civer.authserver.controller;

import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.service.AccountService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 账号自助（管理员与普通用户共用）。
 *
 * 路径刻意放在 /account 而不是 /admin/**：后者整体要求 ROLE_ADMIN，
 * 在 URL 规则上开白名单容易误放开别的管理端点。主体一律从
 * &#64;AuthenticationPrincipal 取，接口不接收用户 id。
 *
 * 改密 / 改名成功后在同一个请求里清掉本机会话并回到登录页：
 * AccountService 已吊销全部令牌，会话里的旧身份也不能继续用。
 */
@Controller
@RequestMapping("/account")
public class AccountController {

	private final AccountService accountService;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	public String index(@AuthenticationPrincipal UserDetails principal, Model model) {
		String name = principal.getUsername();
		model.addAttribute("account", accountService.current(name));
		model.addAttribute("active", "profile");
		return "account/index";
	}

	@GetMapping("/authorizations")
	public String authorizations(@AuthenticationPrincipal UserDetails principal, Model model) {
		model.addAttribute("authorizations", accountService.authorizations(principal.getUsername()));
		model.addAttribute("active", "profile");
		return "account/authorizations";
	}

	@PostMapping("/profile")
	public String updateProfile(@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) String email,
			@RequestParam(required = false) String nickname,
			RedirectAttributes ra) {
		try {
			accountService.updateProfile(principal.getUsername(), email, nickname);
			ra.addFlashAttribute("ok", "资料已保存。邮箱修改后会标记为未验证。");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/account";
	}

	@PostMapping("/password")
	public String changePassword(@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) String currentPassword,
			@RequestParam(required = false) String newPassword,
			@RequestParam(required = false) String confirmPassword,
			HttpServletRequest request, HttpServletResponse response,
			RedirectAttributes ra) {
		try {
			accountService.changePassword(principal.getUsername(), currentPassword,
					newPassword, confirmPassword);
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/account";
		}
		return logoutAndReturn(request, response);
	}

	@PostMapping("/username")
	public String changeUsername(@AuthenticationPrincipal UserDetails principal,
			@RequestParam(required = false) String newUsername,
			@RequestParam(required = false) String currentPassword,
			HttpServletRequest request, HttpServletResponse response,
			RedirectAttributes ra) {
		try {
			accountService.changeUsername(principal.getUsername(), newUsername, currentPassword);
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/account";
		}
		return logoutAndReturn(request, response);
	}

	@PostMapping("/authorizations/{clientId}/revoke")
	public String revoke(@AuthenticationPrincipal UserDetails principal,
			@PathVariable String clientId,
			RedirectAttributes ra) {
		try {
			int revoked = accountService.revoke(principal.getUsername(), clientId);
			ra.addFlashAttribute("ok", revoked > 0
					? "已撤销该应用的授权，清理了 " + revoked + " 条令牌记录。"
					: "已撤销该应用的授权。");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/account/authorizations";
	}

	private String logoutAndReturn(HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		logoutHandler.logout(request, response, authentication);
		return "redirect:/login?logout";
	}
}
