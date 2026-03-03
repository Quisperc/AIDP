package cn.civer.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

	@org.springframework.beans.factory.annotation.Autowired
	private cn.civer.authserver.handler.SsoLogoutSuccessHandler ssoLogoutSuccessHandler;

	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers("/", "/login", "/favicon.ico", "/css/**", "/js/**", "/images/**", "/error", "/.well-known/**").permitAll()
						.requestMatchers("/api/**").hasAuthority("SCOPE_openid")
						.anyRequest().authenticated())
				.formLogin((form) -> form
						.loginPage("/login")
						.permitAll())
				.logout((logout) -> logout
						.logoutUrl("/logout")
						.logoutSuccessHandler(ssoLogoutSuccessHandler)
						.permitAll())
				.oauth2ResourceServer((resourceServer) -> resourceServer
						.jwt(Customizer.withDefaults()));

		return http.build();
	}

	@Bean
	public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
		return new JdbcRegisteredClientRepository(jdbcTemplate);
	}

	@Bean
	public org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService authorizationConsentService(
			JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
		return new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService(
				jdbcTemplate, registeredClientRepository);
	}

	@Bean
	public org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizationService(
			JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
		return new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(
				jdbcTemplate, registeredClientRepository);
	}

}
