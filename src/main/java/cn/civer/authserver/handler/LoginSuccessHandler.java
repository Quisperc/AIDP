package cn.civer.authserver.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录成功后，若保存的请求是 /error（或 /error?*），则改为重定向到 /，避免出现“登录成功却跳到错误页”的现象。
 */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	/** 缓存对象是线程安全且可复用的，原先每次登录都 new 一个 */
	private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {
		SavedRequest savedRequest = requestCache.getRequest(request, response);
		if (savedRequest != null) {
			String redirectUrl = savedRequest.getRedirectUrl();
			// 若保存的请求是 /error 或 /error?*，登录成功后直接回首页，避免误入错误页
			if (redirectUrl != null && (redirectUrl.contains("/error?") || redirectUrl.replaceFirst("\\?.*", "").endsWith("/error"))) {
				getRedirectStrategy().sendRedirect(request, response, "/");
				return;
			}
		}
		super.onAuthenticationSuccess(request, response, authentication);
	}
}
