package cn.civer.authserver.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
public class ErrorPageController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ErrorPageController.class);
	private static final String FRIENDLY_CLIENT_INVALID = "该客户端未在认证中心注册或已失效，请联系系统管理员在认证中心重新配置后再试。";

	@RequestMapping("/error")
	public String handleError(HttpServletRequest request, Model model) {
		Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		Object messageObj = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
		Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

		int status = statusObj != null ? (Integer) statusObj : 500;
		String message = request.getParameter("message"); // 优先使用 URL 参数（如 Consent 重定向传入）
		if (message == null || message.isBlank()) {
			if (ex != null && ex.getMessage() != null) {
				message = ex.getMessage();
			} else if (messageObj != null && !messageObj.toString().isBlank()) {
				message = messageObj.toString();
			} else {
				message = status == 400 ? "请求无效。" : (status == 404 ? "页面不存在。" : "服务器内部错误，请稍后再试。");
			}
		}
		// 将 OAuth2 客户端相关错误统一为友好提示
		message = toFriendlyMessage(status, message);
		log.info("[error page] status={}, message={}, backUrl={}", status, message, request.getParameter("backUrl"));

		model.addAttribute("status", status);
		model.addAttribute("message", message);
		String backUrl = request.getParameter("backUrl");
		model.addAttribute("backUrl", (backUrl != null && !backUrl.isBlank()) ? backUrl : null);
		String statusText;
		try {
			statusText = HttpStatus.valueOf(status).getReasonPhrase();
		} catch (IllegalArgumentException e) {
			statusText = "Error";
		}
		model.addAttribute("statusText", statusText);
		return "error";
	}

	private static String toFriendlyMessage(int status, String message) {
		if (message == null || message.isBlank()) {
			return message;
		}
		// OAuth2 框架返回的 client_id 无效（未注册或参数错误）
		if (message.contains("invalid_request") && message.contains("client_id")) {
			return FRIENDLY_CLIENT_INVALID;
		}
		if (message.contains("OAuth 2.0 Parameter") && message.contains("client")) {
			return FRIENDLY_CLIENT_INVALID;
		}
		if (message.contains("Invalid client") || (message.contains("client") && message.contains("not found"))) {
			return FRIENDLY_CLIENT_INVALID;
		}
		return message;
	}
}
