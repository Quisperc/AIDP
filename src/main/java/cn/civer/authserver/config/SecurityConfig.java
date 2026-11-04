package cn.civer.authserver.config;

import cn.civer.authserver.handler.LoginSuccessHandler;
import cn.civer.authserver.handler.SsoLogoutSuccessHandler;
import cn.civer.authserver.security.LoginRateLimitFilter;
import cn.civer.authserver.service.LoginAttemptService;
import cn.civer.authserver.service.SsoLogoutService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 唯一的安全过滤器链 + 口令编码器。
 *
 * 管理后台并入本应用后，这里只有一种身份来源：**表单登录建立的会话**。
 *
 * 路由分三类：
 * - 匿名可达：静态资源、登录/错误页、健康检查；
 * - OIDC 协议端点：由 Spring Authorization Server 自己的配置接管（授权端点会跳登录）；
 * - /admin/**：必须是 ROLE_ADMIN，URL 粒度足够，控制器上不再逐个加 @PreAuthorize。
 *
 * 命名沿用「SecurityConfig」：这个类管的是认证/授权规则本身。
 * OAuth2 授权服务器的持久化组件（客户端注册表、授权、同意）属于另一层职责，
 * 已拆到 {@link OAuth2PersistenceConfig}。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final SsoLogoutSuccessHandler ssoLogoutSuccessHandler;
	private final LoginSuccessHandler loginSuccessHandler;
	private final LoginAttemptService loginAttemptService;
	private final SsoLogoutService ssoLogoutService;

	public SecurityConfig(SsoLogoutSuccessHandler ssoLogoutSuccessHandler,
			LoginSuccessHandler loginSuccessHandler,
			LoginAttemptService loginAttemptService,
			SsoLogoutService ssoLogoutService) {
		this.ssoLogoutSuccessHandler = ssoLogoutSuccessHandler;
		this.loginSuccessHandler = loginSuccessHandler;
		this.loginAttemptService = loginAttemptService;
		this.ssoLogoutService = ssoLogoutService;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						// 健康检查必须匿名可达，否则容器编排与发布流水线拿到 401/302
						.requestMatchers("/", "/login", "/error", "/favicon.ico", "/css/**", "/js/**", "/images/**",
								"/actuator/health", "/actuator/health/**").permitAll()
						// 账号自助：任何已登录用户都可维护自己的资料与授权，
						// 但只作用于当前会话主体，绝不接受用户 id 参数。
						.requestMatchers("/account", "/account/**").authenticated()
						// 管理台：会话 + 角色。URL 粒度足够，方法上加 @PreAuthorize 反而重复
						.requestMatchers("/admin", "/admin/**").hasAuthority("ROLE_ADMIN")
						.anyRequest().authenticated())
				.formLogin((form) -> form
						.loginPage("/login")
						.successHandler(loginSuccessHandler)
						.permitAll())
				.logout((logout) -> logout
						.logoutUrl("/logout")
						.logoutSuccessHandler(ssoLogoutSuccessHandler)
						.permitAll())
				// 登录表单的限流闸门，必须排在认证过滤器之前
				.addFilterBefore(new LoginRateLimitFilter(loginAttemptService),
						UsernamePasswordAuthenticationFilter.class)
				.oauth2AuthorizationServer(authorizationServer -> authorizationServer
						.oidc(oidc -> oidc
								// 显式广告 profile / email：客户端可从 discovery 得知能力，
								// 不必靠文档约定。scope() 来自 AbstractBuilder。
								.providerConfigurationEndpoint(endpoint -> endpoint
										.providerConfigurationCustomizer(builder -> builder
												.scope(OidcScopes.PROFILE)
												.scope(OidcScopes.EMAIL)))
								// 标准端点 /connect/logout（discovery 的 end_session_endpoint）
								// 也要吊销该用户的授权与令牌，避免子系统仍持有有效令牌。
								.logoutEndpoint(endpoint ->
										endpoint.logoutResponseHandler(oidcLogoutSuccessHandler())))
						.authorizationEndpoint(authorizationEndpoint ->
								authorizationEndpoint.consentPage("/oauth2/consent")));

		return http.build();
	}

	/**
	 * OIDC RP-Initiated Logout 的成功处理器：先做全局退出（删同意/授权），
	 * 再交给 Spring Authorization Server 的默认实现完成会话失效与回跳校验。
	 *
	 * 复用默认实现而不是自己写重定向：post_logout_redirect_uri 的合法性校验、
	 * id_token_hint 校验、以及 state 回传都在它里面，自己实现必然漏。
	 */
	private OidcLogoutAuthenticationSuccessHandler oidcLogoutSuccessHandler() {
		OidcLogoutAuthenticationSuccessHandler handler = new OidcLogoutAuthenticationSuccessHandler();
		handler.setLogoutHandler((request, response, authentication) -> {
			if (authentication == null
					|| !authentication.isAuthenticated()
					|| authentication instanceof AnonymousAuthenticationToken) {
				return;
			}
			ssoLogoutService.performGlobalLogout(authentication.getName());
		});
		return handler;
	}

	/**
	 * 口令编码器属于安全基础设施，放在安全配置里。
	 *
	 * 原先它挂在 CustomUserDetailsService 上（该类同时标了 @Service 与 @Configuration），
	 * 等于让一个业务组件生产基础设施 bean：注入 PasswordEncoder 的任何类都和
	 * 「用户查询服务」产生了隐式依赖，分层也就乱了。
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
