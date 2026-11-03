package cn.civer.authserver.config;

import cn.civer.authserver.handler.LoginSuccessHandler;
import cn.civer.authserver.handler.SsoLogoutSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 唯一的安全过滤器链。
 *
 * 管理后台并入本应用后，这里只有一种身份来源：**表单登录建立的会话**。
 * 原先的「资源服务器 + JWT 转换器」是为管理端远程调用 /api/** 服务的，
 * 现在调用发生在进程内，已经不需要，一并移除。
 *
 * 路由分三类：
 * - 匿名可达：静态资源、登录/错误页、健康检查；
 * - OIDC 协议端点：由 Spring Authorization Server 自己的配置接管（授权端点会跳登录）；
 * - /admin/**：必须是 ROLE_ADMIN，URL 粒度足够，控制器上不再逐个加 @PreAuthorize。
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

	private final SsoLogoutSuccessHandler ssoLogoutSuccessHandler;
	private final LoginSuccessHandler loginSuccessHandler;

	public AuthorizationServerConfig(SsoLogoutSuccessHandler ssoLogoutSuccessHandler,
			LoginSuccessHandler loginSuccessHandler) {
		this.ssoLogoutSuccessHandler = ssoLogoutSuccessHandler;
		this.loginSuccessHandler = loginSuccessHandler;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						// 健康检查必须匿名可达，否则容器编排与发布流水线拿到 401/302
						.requestMatchers("/", "/login", "/error", "/favicon.ico", "/css/**", "/js/**", "/images/**",
								"/actuator/health", "/actuator/health/**").permitAll()
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
				.oauth2AuthorizationServer(authorizationServer -> authorizationServer
						.oidc(Customizer.withDefaults())
						.authorizationEndpoint(authorizationEndpoint ->
								authorizationEndpoint.consentPage("/oauth2/consent")));

		return http.build();
	}

	@Bean
	public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
		return new JdbcRegisteredClientRepository(jdbcTemplate);
	}

	@Bean
	public OAuth2AuthorizationConsentService authorizationConsentService(
			JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
	}

	@Bean
	public OAuth2AuthorizationService authorizationService(
			JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
	}
}
