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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

	private final SsoLogoutSuccessHandler ssoLogoutSuccessHandler;
	private final LoginSuccessHandler loginSuccessHandler;

	// 构造器注入，替代原先的字段注入 + 在配置里 new handler
	public AuthorizationServerConfig(SsoLogoutSuccessHandler ssoLogoutSuccessHandler,
			LoginSuccessHandler loginSuccessHandler) {
		this.ssoLogoutSuccessHandler = ssoLogoutSuccessHandler;
		this.loginSuccessHandler = loginSuccessHandler;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						// 健康检查必须匿名可达，否则容器编排无法探活（会拿到 401/302）
						.requestMatchers("/", "/login", "/error", "/favicon.ico", "/css/**", "/js/**", "/images/**",
								"/actuator/health", "/actuator/health/**").permitAll()
						// /api/** 的资源权限由各 Controller 上的 @PreAuthorize 细分，
						// 这里只要求请求带 openid scope 的合法令牌（粗粒度门槛）。
						.requestMatchers("/api/**").hasAuthority("SCOPE_openid")
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
								authorizationEndpoint.consentPage("/oauth2/consent")))
				.oauth2ResourceServer((resourceServer) -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

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
