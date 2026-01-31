package cn.civer.authserver.config;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class DataInitializer {

	@Bean
	public CommandLineRunner initData(UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			RegisteredClientRepository registeredClientRepository,
			@Value("${app.auth.client-id}") String clientId,
			@Value("${app.auth.client-secret}") String clientSecret,
			@Value("${app.auth.redirect-uri}") String redirectUri) {
		return args -> {
			// Initialize Users
			if (userRepository.count() == 0) {
				userRepository.save(new User("admin", passwordEncoder.encode("password"), "ROLE_ADMIN"));
				userRepository.save(new User("user", passwordEncoder.encode("password"), "ROLE_USER"));
				System.out.println("Default users created: admin/password, user/password");
			}

			// Initialize Client
			if (registeredClientRepository.findByClientId(clientId) == null) {
				RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
						.clientId(clientId)
						.clientSecret(passwordEncoder.encode(clientSecret))
						.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
						.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
						.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
						.redirectUri(redirectUri) // Client App URL
						.scope(OidcScopes.OPENID)
						.scope(OidcScopes.PROFILE)
						.clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
						.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(30)).build())
						.build();
				registeredClientRepository.save(registeredClient);
				System.out.println("Default client created: " + clientId);
			}
		};
	}
}
