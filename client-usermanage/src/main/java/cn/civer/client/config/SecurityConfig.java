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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;
	private final cn.civer.client.handler.CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

	@org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.name}")
	private String cookieName;

	@org.springframework.beans.factory.annotation.Value("${app.auth-server-url}")
	private String authServerUrl;

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

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/sso-logout"))
				.oauth2Login(oauth2 -> oauth2
						.userInfoEndpoint(userInfo -> userInfo
								.oidcUserService(this.oidcUserService()))
						.successHandler(customAuthenticationSuccessHandler))
				.logout(logout -> logout
						.logoutRequestMatcher(
								new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout"))
						// Local logout + Redirect to Auth Server to revoke consent (but keep SSO
						// session)
						.logoutSuccessUrl(String.format("%s/oauth2/revoke-consent?client_id=%s&redirect_uri=%s/",
								authServerUrl, clientId, baseUrl))
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies(cookieName))
				.sessionManagement(session -> session
						.maximumSessions(1)
						.sessionRegistry(sessionRegistry()));
		return http.build();
	}

	@Bean
	public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
		return new org.springframework.security.core.session.SessionRegistryImpl();
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
