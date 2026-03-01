package cn.civer.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
	private final org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;
	private final cn.civer.client.handler.CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

	private static final String REGISTRATION_ID = "oidc-client";

	@org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.name}")
	private String cookieName;

	@org.springframework.beans.factory.annotation.Value("${app.base-url}")
	private String baseUrl;

	@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.oidc-client.client-id}")
	private String clientId;

	public SecurityConfig(
			org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository,
			cn.civer.client.handler.CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler) {
		this.clientRegistrationRepository = clientRegistrationRepository;
		this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
	}

	/** 从 OAuth2 Client 的 issuer-uri 取得认证中心地址，与 spring.security.oauth2.client.provider.*.issuer-uri 一致 */
	private String getAuthServerIssuerUri() {
		var reg = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
		if (reg == null) {
			throw new IllegalStateException("OAuth2 client registration '" + REGISTRATION_ID + "' not found");
		}
		String issuer = reg.getProviderDetails().getIssuerUri();
		if (issuer != null) {
			return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		}
		// 兼容：从 token endpoint 推导 base URL
		String tokenUri = reg.getProviderDetails().getTokenUri();
		return tokenUri.replaceFirst("/oauth2/token$", "").replaceFirst("/oauth2/token\\?.*$", "");
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
						.requestMatchers("/error", "/login").permitAll()
						.requestMatchers(new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
								"/api/sso-logout"))
						.permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.ignoringRequestMatchers(
						new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/sso-logout")))
				.oauth2Login(oauth2 -> oauth2
						.loginPage("/login")
						.failureHandler(this::onOAuth2LoginFailure)
						.userInfoEndpoint(userInfo -> userInfo
								.oidcUserService(this.oidcUserService()))
						.successHandler(customAuthenticationSuccessHandler))
				.logout(logout -> logout
						.logoutRequestMatcher(
								new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout"))
						// Local logout + Redirect to Auth Server to revoke consent (but keep SSO
						// session)
						.logoutSuccessUrl(String.format("%s/oauth2/revoke-consent?client_id=%s&redirect_uri=%s/",
								getAuthServerIssuerUri(), clientId, baseUrl))
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(cookieName))
				.sessionManagement(session -> session
						.maximumSessions(1)
						.expiredUrl("/")
						.sessionRegistry(sessionRegistry()));
		return http.build();
	}

	@Bean
	public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
		return new org.springframework.security.core.session.SessionRegistryImpl();
	}

	/**
	 * OAuth2 登录失败时区分「客户端配置错误」（如密钥错误）与其它认证失败，便于登录页展示不同提示。
	 */
	private void onOAuth2LoginFailure(HttpServletRequest request, HttpServletResponse response,
			org.springframework.security.core.AuthenticationException exception) throws IOException, ServletException {
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
		new SimpleUrlAuthenticationFailureHandler("/login?" + errorParam).onAuthenticationFailure(request, response, exception);
	}

	private static boolean isClientConfigError(Throwable t) {
		for (Throwable ex = t; ex != null; ex = ex.getCause()) {
			String msg = ex.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase();
				if (lower.contains("invalid_client") || lower.contains("invalid_grant")
						|| lower.contains("invalid_token_response")
						|| lower.contains("client_secret") || lower.contains("client credentials")
						|| lower.contains("401") && (lower.contains("unauthorized") || lower.contains("authorization required"))) {
					return true;
				}
			}
		}
		return false;
	}

	private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
		final OidcUserService delegate = new OidcUserService();

		return (userRequest) -> {
			// Delegate to the default implementation for loading a user
			OidcUser oidcUser = delegate.loadUser(userRequest);

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
