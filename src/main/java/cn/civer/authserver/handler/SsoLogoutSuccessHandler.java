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
		String redirectUri = request.getParameter("redirect_uri");
		boolean allowRedirect = false;
		if (redirectUri != null && !redirectUri.isBlank()) {
			try {
				allowRedirect = ssoLogoutService.isAllowedLogoutRedirect(redirectUri);
			} catch (Throwable t) {
				// 未重新编译 auth-server 时可能报 NoSuchMethodError（Error），忽略后使用默认登出页
			}
		}
		if (allowRedirect) {
			response.sendRedirect(redirectUri);
		} else {
			response.sendRedirect("/login?logout");
		}
	}
}
