package cn.civer.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.name}")
	private String cookieName;

	@org.springframework.beans.factory.annotation.Value("${app.auth-server-url}")
	private String authServerUrl;

	@org.springframework.beans.factory.annotation.Value("${app.base-url}")
	private String baseUrl;

	@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.oidc-client.client-id}")
	private String clientId;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/error", "/login").permitAll()
						.requestMatchers("/api/sso-logout").permitAll()
						.anyRequest().authenticated())
				// Allow broadcast logout from Auth Server without CSRF token
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/sso-logout"))
				.oauth2Login(oauth2 -> oauth2
						.loginPage("/login")
						.userInfoEndpoint(userInfo -> userInfo
								.oidcUserService(this.oidcUserService())))
				.logout(logout -> logout
						// Allow GET request for logout
						.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
						// 1. Invalidate Local Session
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(cookieName)
						// 2. Redirect to Auth Server to revoke ONLY this client's consent
						// (SSO session remains active)
						.logoutSuccessUrl(String.format("%s/oauth2/revoke-consent?client_id=%s&redirect_uri=%s/",
								authServerUrl, clientId, baseUrl)))
				// Enable SessionRegistry for Broadcast Logout
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
	 * Map "roles" claim from ID Token to Spring Security Authorities
	 */
	private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
		final OidcUserService delegate = new OidcUserService();

		return (userRequest) -> {
			OidcUser oidcUser = delegate.loadUser(userRequest);
			Collection<GrantedAuthority> mappedAuthorities = new HashSet<>();

			List<String> roles = oidcUser.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(role -> mappedAuthorities.add(new SimpleGrantedAuthority(role)));
			}
			mappedAuthorities.addAll(oidcUser.getAuthorities());

			return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
		};
	}
}
