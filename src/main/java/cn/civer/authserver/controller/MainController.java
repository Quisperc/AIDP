package cn.civer.authserver.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MainController {

	@GetMapping("/")
	public String index(org.springframework.security.core.Authentication authentication,
			Model model) {
		if (authentication != null && authentication.isAuthenticated()) {
			// 展示名由 FooterModelAdvice 的 displayName 统一提供（昵称优先），
			// 这里不再往模型里塞 username，避免和 internal principal_name 混淆。
			model.addAttribute("roles", authentication.getAuthorities());
			return "login_success";
		}
		return "welcome";
	}

	@GetMapping("/login")
	public String login(org.springframework.security.core.Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()) {
			return "redirect:/";
		}
		return "login";
	}

	/**
	 * 显式处理 GET /error，避免直接访问 /error?continue 时由 BasicErrorController 处理导致 500/999。
	 * - 若为容器异常转发（带 error 属性）：使用请求中的 status/message 等，保证真实异常信息不丢失。
	 * - 若为直接访问（如 redirect 到 /error?message=...）：使用 query 参数渲染错误页。
	 */
	@GetMapping("/error")
	public String error(
			@RequestParam(value = "message", required = false) String message,
			@RequestParam(value = "continue", required = false) String continueUrl,
			HttpServletRequest request,
			Model model) {
		Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (statusCode != null) {
			// 来自异常转发：使用容器设置的错误属性，不覆盖真实异常
			model.addAttribute("status", statusCode);
			model.addAttribute("statusText", getStatusText(statusCode));
			Object msg = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
			model.addAttribute("message", msg != null && StringUtils.hasText(msg.toString()) ? msg.toString() : "请求处理出错，请稍后重试。");
			model.addAttribute("backUrl", safeBackUrl(continueUrl));
		} else {
			// 直接访问 /error（如 Consent 重定向或登录后误入）
			model.addAttribute("status", 400);
			model.addAttribute("statusText", "Bad Request");
			model.addAttribute("message", StringUtils.hasText(message)
					? message
					: "请求无效或页面不存在，请返回首页重试。");
			model.addAttribute("backUrl", safeBackUrl(continueUrl));
		}
		return "error";
	}

	/**
	 * 「返回」按钮的落点只接受站内相对路径。
	 *
	 * continue 直接来自 query 参数，若原样塞进 href：
	 * - /error?continue=javascript:alert(document.cookie) —— 用户点「返回」就在**认证中心域名**下执行脚本，
	 *   而这里正是持有 SSO 会话的地方；
	 * - /error?continue=//evil.com —— 协议相对 URL，开放重定向的经典绕过写法。
	 * 两者都不合法时返回 null，模板会退化成 history.back()，不丢功能。
	 */
	private static String safeBackUrl(String continueUrl) {
		if (!StringUtils.hasText(continueUrl)) {
			return null;
		}
		String value = continueUrl.trim();
		if (value.startsWith("/") && !value.startsWith("//")) {
			return value;
		}
		return null;
	}

	private static String getStatusText(int code) {
		return switch (code) {
			case 400 -> "Bad Request";
			case 401 -> "Unauthorized";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 500 -> "Internal Server Error";
			default -> "Error";
		};
	}
}
