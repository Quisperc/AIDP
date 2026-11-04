package cn.civer.authserver.handler;

import cn.civer.authserver.service.SsoLogoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 表单登出成功处理：吊销该用户的授权与令牌，然后固定回到登录页。
 *
 * 刻意不读取请求里的 redirect_uri：Spring Security 的表单登出没有这个标准参数，
 * 接受外部 URL 回跳会引入开放重定向面。需要回到业务系统时，由业务系统按
 * OIDC RP-Initiated Logout 走 /connect/logout + 已注册的 post_logout_redirect_uri，
 * 回跳地址由框架做精确匹配校验——这才是标准且无需自研白名单的做法。
 */
@Component
public class SsoLogoutSuccessHandler implements LogoutSuccessHandler {

	private final SsoLogoutService ssoLogoutService;

	public SsoLogoutSuccessHandler(SsoLogoutService ssoLogoutService) {
		this.ssoLogoutService = ssoLogoutService;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException {
		if (authentication != null && authentication.getName() != null) {
			ssoLogoutService.performGlobalLogout(authentication.getName());
		}
		response.sendRedirect("/login?logout");
	}
}
