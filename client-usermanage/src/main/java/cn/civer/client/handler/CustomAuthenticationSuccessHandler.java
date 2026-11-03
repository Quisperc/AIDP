package cn.civer.client.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

/**
 * 登录成功后的跳转。
 *
 * 前后端分离后有多种部署形态，这里按优先级决定落点：
 * 1. 请求带 redirect 参数且合法 —— 回跳前端指定页面（跨域部署时由前端传入完整 URL）；
 * 2. 配置了 app.frontend-url —— 回跳前端站点首页；
 * 3. 都没有 —— 沿用 Spring 的默认行为（savedRequest 或根路径），即同源部署。
 *
 * 安全：redirect 参数必须校验。若直接采信就是开放重定向，
 * 攻击者可构造 ?redirect=https://evil.com 借本站完成登录后把用户送走。
 * 这里只接受 https 且与 app.frontend-url 同 host 的地址。
 */
@Component
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

	private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
	private final String frontendUrl;

	public CustomAuthenticationSuccessHandler(
			@Value("${app.frontend-url:}") String frontendUrl) {
		this.frontendUrl = frontendUrl == null ? "" : frontendUrl.trim();
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {

		String targetUrl = resolveTarget(request, response);
		log.debug("[login-success] redirecting to {}", targetUrl);
		getRedirectStrategy().sendRedirect(request, response, targetUrl);
	}

	private String resolveTarget(HttpServletRequest request, HttpServletResponse response) {
		String requested = request.getParameter("redirect");
		if (isAllowedRedirect(requested)) {
			return requested.trim();
		}

		if (!frontendUrl.isEmpty()) {
			return frontendUrl;
		}

		var savedRequest = requestCache.getRequest(request, response);
		String targetUrl = (savedRequest != null) ? savedRequest.getRedirectUrl() : "/";

		// Strip 'continue' parameter if present
		if (targetUrl != null && targetUrl.contains("continue")) {
			targetUrl = UriComponentsBuilder.fromUriString(targetUrl)
					.replaceQueryParam("continue")
					.build()
					.toUriString();
		}
		return (targetUrl == null || targetUrl.isEmpty()) ? "/" : targetUrl;
	}

	/** 仅允许 https 且与 app.frontend-url 同 host 的回跳地址 */
	private boolean isAllowedRedirect(String url) {
		if (url == null || url.isBlank() || frontendUrl.isEmpty()) {
			return false;
		}
		try {
			URI target = new URI(url.trim());
			URI base = new URI(frontendUrl);
			if (!"https".equalsIgnoreCase(target.getScheme())) {
				return false;
			}
			if (target.getHost() == null || base.getHost() == null) {
				return false;
			}
			return target.getHost().equalsIgnoreCase(base.getHost());
		} catch (Exception e) {
			log.debug("[login-success] rejected redirect param: {}", url);
			return false;
		}
	}
}
