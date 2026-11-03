package cn.civer.authserver.security;

import cn.civer.authserver.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录表单的限流闸门：在认证发生**之前**拦下已被锁定的用户名。
 *
 * 放在 UsernamePasswordAuthenticationFilter 之前，而不是在自定义
 * AuthenticationProvider 里判定，是因为这里只需要一个判断 + 一次重定向，
 * 不必卷入认证异常体系（否则每种异常都要想一遍映射成哪个登录页参数）。
 *
 * 只处理 POST /login：GET 只是渲染表单，没有猜解成本。
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

	private final LoginAttemptService loginAttemptService;

	public LoginRateLimitFilter(LoginAttemptService loginAttemptService) {
		this.loginAttemptService = loginAttemptService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// 用 requestURI 而不是 getServletPath()：DispatcherServlet 映射为 "/" 时
		// servletPath 可能为空，过滤器会静默失效（限流看起来「配了但没生效」）
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !"POST".equalsIgnoreCase(request.getMethod()) || !"/login".equals(path);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String username = request.getParameter("username");
		if (loginAttemptService.isBlocked(username)) {
			log.warn("[login-guard] blocked login attempt: username={}, ip={}",
					username, request.getRemoteAddr());
			response.sendRedirect(request.getContextPath() + "/login?blocked");
			return;
		}
		filterChain.doFilter(request, response);
	}
}
