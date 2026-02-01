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
				.oauth2Login(oauth2 -> oauth2
						.userInfoEndpoint(userInfo -> userInfo
								.oidcUserService(this.oidcUserService()))
						.successHandler(customAuthenticationSuccessHandler))
				.logout(logout -> logout
						.logoutSuccessHandler(oidcLogoutSuccessHandler()));
		return http.build();
	}

	private org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler() {
		org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler successHandler = new org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler(
				this.clientRegistrationRepository);

		// Dynamically derive Auth Server Login URL from the "oidc-client" registration
		String authServerLoginUrl = "http://127.0.0.1:8080/login"; // Fallback

		try {
			var registration = this.clientRegistrationRepository.findByRegistrationId("oidc-client");
			if (registration != null) {
				String issuer = registration.getProviderDetails().getIssuerUri();
				if (issuer != null) {
					// Ensure no double slashes if issuer ends with /
					authServerLoginUrl = issuer.replaceAll("/$", "") + "/login";
				}
			}
		} catch (Exception e) {
			// Log error or stick to fallback
		}

		successHandler.setPostLogoutRedirectUri(authServerLoginUrl);
		return successHandler;
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
