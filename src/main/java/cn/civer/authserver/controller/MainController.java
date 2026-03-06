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
			model.addAttribute("username", authentication.getName());
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
			model.addAttribute("backUrl", StringUtils.hasText(continueUrl) ? continueUrl : null);
		} else {
			// 直接访问 /error（如 Consent 重定向或登录后误入）
			model.addAttribute("status", 400);
			model.addAttribute("statusText", "Bad Request");
			model.addAttribute("message", StringUtils.hasText(message)
					? message
					: "该客户端未在认证中心注册或已失效，请联系系统管理员在认证中心重新配置后再试。");
			model.addAttribute("backUrl", StringUtils.hasText(continueUrl) ? continueUrl : null);
		}
		return "error";
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
