package cn.civer.authserver.config;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	@Value("${app.auth.initial-client.client-id}")
	private String clientId;

	@Value("${app.auth.initial-client.client-secret}")
	private String clientSecret;

	@Value("${app.auth.initial-client.redirect-uris}")
	private String[] redirectUris;

	@Value("${app.auth.initial-client.post-logout-redirect-uri}")
	private String postLogoutRedirectUri;

	@Bean
	public CommandLineRunner initData(UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			RegisteredClientRepository registeredClientRepository) {
		return args -> {
			// Initialize Users
			if (userRepository.count() == 0) {
				userRepository.save(new User("admin", passwordEncoder.encode("password"), "ROLE_ADMIN"));
				userRepository.save(new User("user", passwordEncoder.encode("password"), "ROLE_USER"));
				log.info("Default users created: admin/password, user/password");
			}

			// Initialize default client
			if (registeredClientRepository.findByClientId(clientId) == null) {
				RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
						.clientId(clientId)
						.clientSecret(passwordEncoder.encode(clientSecret))
						.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
						.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
						.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
						.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
				for (String uri : redirectUris) {
					builder = builder.redirectUri(uri);
				}
				RegisteredClient registeredClient = builder
						.postLogoutRedirectUri(postLogoutRedirectUri)
						.scope(OidcScopes.OPENID)
						.scope(OidcScopes.PROFILE)
						.clientSettings(ClientSettings.builder()
								.requireAuthorizationConsent(true)
								.requireProofKey(true)
								.build())
						.tokenSettings(TokenSettings.builder()
								.accessTokenTimeToLive(Duration.ofMinutes(30))
								.build())
						.build();

				registeredClientRepository.save(registeredClient);
				log.info("Initialized default client: {}", clientId);
			} else {
				log.info("Client {} already exists, skipping initialization.", clientId);
			}
		};
	}
}
