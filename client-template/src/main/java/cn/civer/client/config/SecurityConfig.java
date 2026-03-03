package cn.civer.client.config;

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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

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

	@Value("${server.servlet.session.cookie.name}")
	private String cookieName;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${spring.security.oauth2.client.registration.oidc-client.client-id}")
	private String clientId;

	public SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
		this.clientRegistrationRepository = clientRegistrationRepository;
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
		var tokenUri = reg.getProviderDetails().getTokenUri();
		return tokenUri.replaceFirst("/oauth2/token$", "").replaceFirst("/oauth2/token\\?.*$", "");
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/error", "/login").permitAll()
						.requestMatchers("/api/sso-logout").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/sso-logout"))
				.oauth2Login(oauth2 -> oauth2
						.loginPage("/login")
						.failureHandler(this::onOAuth2LoginFailure)
						.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService())))
				.logout(logout -> logout
						.logoutUrl("/logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(cookieName)
						.logoutSuccessUrl(String.format("%s/oauth2/revoke-consent?client_id=%s&redirect_uri=%s/",
								getAuthServerIssuerUri(), clientId, baseUrl)))
				.sessionManagement(session -> session
						.maximumSessions(1)
						.expiredUrl("/")
						.sessionRegistry(sessionRegistry()));
		return http.build();
	}

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

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
		if (!"error=client_config".equals(errorParam) && isClientConfigError(exception)) {
			errorParam = "error=client_config";
		}
		log.warn("[OAuth2 login failure] exception={}, errorCode={}, redirectParam={}, message={}",
				exception.getClass().getSimpleName(), code, errorParam, exception.getMessage());
		new SimpleUrlAuthenticationFailureHandler("/login?" + errorParam)
				.onAuthenticationFailure(request, response, exception);
	}

	private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
		var delegate = new OidcUserService();
		return userRequest -> {
			var oidcUser = delegate.loadUser(userRequest);
			Collection<GrantedAuthority> mappedAuthorities = new HashSet<>();
			List<String> roles = oidcUser.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(role -> mappedAuthorities.add(new SimpleGrantedAuthority(role)));
			}
			mappedAuthorities.addAll(oidcUser.getAuthorities());
			return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
		};
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
}
