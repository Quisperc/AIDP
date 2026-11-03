package cn.civer.client.config;

import cn.civer.client.handler.CustomAuthenticationSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
	private static final String REGISTRATION_ID = "oidc-client";

	private final ClientRegistrationRepository clientRegistrationRepository;
	private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

	@Value("${server.servlet.session.cookie.name}")
	private String cookieName;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${spring.security.oauth2.client.registration.oidc-client.client-id}")
	private String clientId;

	/** 跨域部署（前端在 EdgeOne Pages）时设为 None，同源部署保持 Lax */
	@Value("${app.cookie.same-site:Lax}")
	private String cookieSameSite;

	public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository,
						  CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler) {
		this.clientRegistrationRepository = clientRegistrationRepository;
		this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
	}

	/** 从 OAuth2 Client 的 issuer-uri 取得认证中心地址，与 spring.security.oauth2.client.provider.*.issuer-uri 一致 */
	private String getAuthServerIssuerUri() {
		var reg = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
		if (reg == null) {
			throw new IllegalStateException("OAuth2 client registration '" + REGISTRATION_ID + "' not found");
		}
		var issuer = reg.getProviderDetails().getIssuerUri();
		if (issuer != null) {
			return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		}
		// 兼容：从 token endpoint 推导 base URL
		var tokenUri = reg.getProviderDetails().getTokenUri();
		return tokenUri.replaceFirst("/oauth2/token$", "").replaceFirst("/oauth2/token\\?.*$", "");
	}

	/**
	 * CSRF 令牌以 Cookie 形式下发（前端读 XSRF-TOKEN 回传 X-XSRF-TOKEN 头）。
	 *
	 * 跨域部署时该 Cookie 必须 SameSite=None; Secure —— 否则浏览器不会在跨站请求中
	 * 携带它，前端读不到 Token，所有 POST/PUT/DELETE 都会被拒。
	 * 同源部署保持默认的 Lax 即可，由 app.cookie.same-site 控制。
	 */
	@Bean
	public CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> {
			cookie.sameSite(cookieSameSite);
			cookie.secure("None".equalsIgnoreCase(cookieSameSite));
			cookie.httpOnly(false);
			cookie.path("/");
		});
		return repository;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						// 数据接口：管理功能限管理员，/api/me 只要登录即可
						.requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
						.requestMatchers("/api/me").authenticated()
						// 认证中心的后端登出回调，以及 SPA 的静态资源与前端路由
						.requestMatchers("/api/sso-logout").permitAll()
						.anyRequest().permitAll())
				/*
				 * 未认证访问数据接口时返回 401，而不是重定向到登录页：
				 * SPA 的 fetch 收到的是一段 HTML 登录页而非 401，只能靠解析响应猜状态。
				 * 改成 401 后前端可明确判断并跳转 /oauth2/authorization/oidc-client。
				 *
				 * 注意 anyRequest().permitAll() 是因为 SPA 的前端路由（/users、/clients 等）
				 * 本就需要匿名可达，页面内的权限由 /admin/** 的数据接口把关。
				 * 后续新增后端接口时，务必显式归入上面的认证规则，不要依赖这一条兜底。
				 */
				.exceptionHandling(ex -> ex
						.defaultAuthenticationEntryPointFor(
								new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
								// 用函数式 matcher：Spring Security 7 默认走 PathPattern，
								// AntPathRequestMatcher 已不适用
								request -> request.getRequestURI().startsWith("/admin/")
										|| request.getRequestURI().equals("/api/me")))
				/*
				 * 前后端分离后，页面不再由服务端渲染，CSRF Token 无法通过模板注入表单。
				 * 改用 Cookie 仓库（HttpOnly=false）把令牌放进 XSRF-TOKEN，
				 * 前端从 cookie 读出后回传 X-XSRF-TOKEN 头。
				 *
				 * 后端登出回调来自认证中心，拿不到本站 cookie，必须豁免。
				 */
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository())
						.ignoringRequestMatchers("/api/sso-logout"))
				// 前端在 EdgeOne Pages 独立域名时跨域：来源由 app.cors.allowed-origins 显式列出
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.oauth2Login(oauth2 -> oauth2
						.failureHandler(this::onOAuth2LoginFailure)
						.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
						.successHandler(customAuthenticationSuccessHandler))
				.logout(logout -> logout
						// Spring Security 7 默认使用 PathPattern 并要求 POST /logout
						.logoutUrl("/logout")
						// Local logout + Redirect to Auth Server to revoke consent (but keep SSO session)
						.logoutSuccessUrl(String.format("%s/oauth2/revoke-consent?client_id=%s&redirect_uri=%s/",
								getAuthServerIssuerUri(), clientId, baseUrl))
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(cookieName))
				.sessionManagement(session -> session
						.maximumSessions(1)
						.expiredUrl("/")
						.sessionRegistry(sessionRegistry));
		return http.build();
	}

	/*
	 * SessionRegistry 的 bean 定义在 SessionRegistryConfig：
	 * 单机用内存实现，Redis 模式换成 SpringSessionBackedSessionRegistry，
	 * 否则即使 session 共享了，maximumSessions(1) 仍只在单个实例内生效。
	 */

	/**
	 * OAuth2 登录失败时区分「客户端配置错误」（如密钥错误）与其它认证失败，便于登录页展示不同提示。
	 */
	private void onOAuth2LoginFailure(HttpServletRequest request, HttpServletResponse response,
									  org.springframework.security.core.AuthenticationException exception)
			throws IOException, ServletException {
		String errorParam = "error";
		String code = null;
		if (exception instanceof OAuth2AuthenticationException oauth2Ex && oauth2Ex.getError() != null) {
			code = oauth2Ex.getError().getErrorCode();
			if ("invalid_client".equals(code) || "invalid_grant".equals(code) || "invalid_token_response".equals(code)) {
				errorParam = "error=client_config";
			}
		}
		// Token 换发失败（如 401 密钥错误）时错误码常为 invalid_token_response，或信息在 message 中
		if (!"error=client_config".equals(errorParam) && isClientConfigError(exception)) {
			errorParam = "error=client_config";
		}
		log.warn("[OAuth2 login failure] exception={}, errorCode={}, redirectParam={}, message={}",
				exception.getClass().getSimpleName(), code, errorParam, exception.getMessage());
		new SimpleUrlAuthenticationFailureHandler("/login?" + errorParam)
				.onAuthenticationFailure(request, response, exception);
	}

	private static boolean isClientConfigError(Throwable t) {
		for (Throwable ex = t; ex != null; ex = ex.getCause()) {
			var msg = ex.getMessage();
			if (msg != null) {
				var lower = msg.toLowerCase();
				if (lower.contains("invalid_client") || lower.contains("invalid_grant")
						|| lower.contains("invalid_token_response")
						|| lower.contains("client_secret") || lower.contains("client credentials")
						|| (lower.contains("401") && (lower.contains("unauthorized") || lower.contains("authorization required")))) {
					return true;
				}
			}
		}
		return false;
	}

	private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
		var delegate = new OidcUserService();

		return userRequest -> {
			// Delegate to the default implementation for loading a user
			var oidcUser = delegate.loadUser(userRequest);

			// Fetch the authority information from the protected resource or ID Token
			Collection<GrantedAuthority> mappedAuthorities = new HashSet<>();

			// map from "roles" claim
			List<String> roles = oidcUser.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(role -> mappedAuthorities.add(new SimpleGrantedAuthority(role)));
			}

			// Also keep original authorities (like scopes)
			mappedAuthorities.addAll(oidcUser.getAuthorities());

			return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
		};
	}
}
