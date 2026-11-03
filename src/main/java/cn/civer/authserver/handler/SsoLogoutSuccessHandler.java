package cn.civer.authserver.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import cn.civer.authserver.service.SsoLogoutService;

import java.io.IOException;

@Component
public class SsoLogoutSuccessHandler implements LogoutSuccessHandler {

	private final SsoLogoutService ssoLogoutService;

	public SsoLogoutSuccessHandler(SsoLogoutService ssoLogoutService) {
		this.ssoLogoutService = ssoLogoutService;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		if (authentication != null && authentication.getName() != null) {
			String issuer = ssoLogoutService.getResolvedIssuer(request);
			ssoLogoutService.performGlobalLogout(authentication.getName(), issuer);
		}
		// 回跳地址只接受已注册客户端 host 白名单内的 URL；isAllowedLogoutRedirect 内部
		// 已对解析失败返回 false，这里不需要再包一层 catch —— 原先捕获 Throwable
		// 是为了绕过一个早已不存在的 NoSuchMethodError，反而会把真实错误吞掉。
		String redirectUri = request.getParameter("redirect_uri");
		if (redirectUri != null && !redirectUri.isBlank()
				&& ssoLogoutService.isAllowedLogoutRedirect(redirectUri)) {
			response.sendRedirect(redirectUri);
		} else {
			response.sendRedirect("/login?logout");
		}
	}
}
