package cn.civer.authserver.controller;

import cn.civer.authserver.handler.SsoLogoutSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

/**
 * 通过浏览器触发的全局退出入口：清除 SSO 会话并对所有客户端执行 Back-Channel Logout。
 */
@Controller
public class GlobalLogoutController {

	private final SsoLogoutSuccessHandler ssoLogoutSuccessHandler;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public GlobalLogoutController(SsoLogoutSuccessHandler ssoLogoutSuccessHandler) {
		this.ssoLogoutSuccessHandler = ssoLogoutSuccessHandler;
	}

	@GetMapping("/oauth2/logout-all")
	public void logoutAll(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		// 清除当前 SSO 会话
		logoutHandler.logout(request, response, authentication);
		// 执行全局退出逻辑：广播 Back-Channel Logout + 根据 redirect_uri 决定跳转
		ssoLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);
	}
}

