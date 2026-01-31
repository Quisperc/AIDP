package cn.civer.template.config;

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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

	public SecurityConfig(
			org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository) {
		this.clientRegistrationRepository = clientRegistrationRepository;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/public/**", "/css/**", "/js/**").permitAll() // Allow home and assets
						.anyRequest().authenticated())
				.oauth2Login(oauth2 -> oauth2
						.userInfoEndpoint(userInfo -> userInfo
								.oidcUserService(this.oidcUserService())))
				.logout(logout -> logout
						.logoutSuccessHandler(oidcLogoutSuccessHandler()));
		return http.build();
	}

	private org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler() {
		var successHandler = new org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler(
				this.clientRegistrationRepository);

		// Dynamically derive Auth Server Login URL for redirect
		String authServerLoginUrl = "http://127.0.0.1:8080/login";
		try {
			var registration = this.clientRegistrationRepository.findByRegistrationId("oidc-client");
			if (registration != null) {
				String issuer = registration.getProviderDetails().getIssuerUri();
				if (issuer != null) {
					authServerLoginUrl = issuer.replaceAll("/$", "") + "/login";
				}
			}
		} catch (Exception e) {
			// ignore
		}
		successHandler.setPostLogoutRedirectUri(authServerLoginUrl);
		return successHandler;
	}

	private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
		final OidcUserService delegate = new OidcUserService();

		return (userRequest) -> {
			OidcUser oidcUser = delegate.loadUser(userRequest);
			Collection<GrantedAuthority> mappedAuthorities = new HashSet<>();

			// Extract "roles" from ID Token claim
			List<String> roles = oidcUser.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(role -> mappedAuthorities.add(new SimpleGrantedAuthority(role)));
			}

			mappedAuthorities.addAll(oidcUser.getAuthorities());
			return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
		};
	}
}
