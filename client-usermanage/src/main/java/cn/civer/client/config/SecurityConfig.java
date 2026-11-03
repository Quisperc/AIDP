package cn.civer.client.config;

import cn.civer.client.handler.CustomAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.HashSet;
import java.util.List;

/**
 * 管理端安全配置：本应用现在只提供 REST API，页面全部由 EdgeOne Pages 托管。
 *
 * 精简点：
 * - CORS 规则直接在这里定义，不再单独一个配置类；
 * - 认证中心地址直接读 app.auth-server-url，不再从 ClientRegistration 反推；
 * - 登录失败统一重定向前端登录页，删掉原来按异常字符串猜错误类型的一大段逻辑；
 * - 没有静态资源与前端路由后，未知路径一律 denyAll，而不是 permitAll。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final CustomAuthenticationSuccessHandler successHandler;

	@Value("${app.frontend-url:}")
	private String frontendUrl;

	@Value("${server.servlet.session.cookie.name}")
	private String cookieName;

	@Value("${app.cookie.same-site:Lax}")
	private String cookieSameSite;

	@Value("${app.cors.allowed-origins:}")
	private List<String> allowedOrigins;

	public SecurityConfig(CustomAuthenticationSuccessHandler successHandler) {
		this.successHandler = successHandler;
	}

	/** 单机模式（未启用 Redis）用内存会话注册表；Redis 模式由 RedisSessionConfig 提供 */
	@Bean
	@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none", matchIfMissing = true)
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		var source = new UrlBasedCorsConfigurationSource();
		List<String> origins = allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		if (origins.isEmpty()) {
			return source;
		}
		var config = new CorsConfiguration();
		// Allow-Credentials=true 时来源不能是通配，必须逐个列出
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Accept"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						// 健康检查探针必须匿名可达，否则容器编排与发布流水线拿到 401/403
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
						.requestMatchers("/api/me").authenticated()
						// 认证中心的后端登出回调，拿不到本站 cookie，必须放行
						.requestMatchers("/api/sso-logout").permitAll()
						// 页面已全部迁到 EdgeOne，后端不再有可匿名访问的页面路由
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.defaultAuthenticationEntryPointFor(
								new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
								request -> request.getRequestURI().startsWith("/admin/")
										|| request.getRequestURI().equals("/api/me")))
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository())
						.ignoringRequestMatchers("/api/sso-logout"))
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.oauth2Login(oauth2 -> oauth2
						.failureUrl(frontendUrl + "/login?error=oauth2")
						.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
						.successHandler(successHandler))
				.logout(logout -> logout
						.logoutUrl("/logout")
						// 全局登出由前端跳 /oauth2/logout-all 完成，这里只做本地清理
						.logoutSuccessUrl(frontendUrl + "/login")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(cookieName))
				.sessionManagement(session -> session
						.maximumSessions(1)
						.expiredUrl(frontendUrl + "/")
						.sessionRegistry(sessionRegistry));
		return http.build();
	}

	/** 跨域时 CSRF cookie 必须 SameSite=None; Secure，否则前端读不到 XSRF-TOKEN */
	private CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> {
			cookie.sameSite(cookieSameSite);
			cookie.secure("None".equalsIgnoreCase(cookieSameSite));
			cookie.httpOnly(false);
			cookie.path("/");
		});
		return repository;
	}

	/** 把 ID Token 里的 roles claim 映射成 ROLE_* 权限，供 /admin/** 使用 */
	private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
		OidcUserService delegate = new OidcUserService();
		return request -> {
			OidcUser user = delegate.loadUser(request);
			var authorities = new HashSet<GrantedAuthority>(user.getAuthorities());
			List<String> roles = user.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
			}
			return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
		};
	}
}
