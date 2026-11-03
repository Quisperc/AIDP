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

	@Value("${app.auth.access-token-time-to-live:30m}")
	private Duration accessTokenTtl;

	/** 是否写入默认演示账号。生产环境应设为 false 并自行初始化账号。 */
	@Value("${app.bootstrap.seed-demo-users:true}")
	private boolean seedDemoUsers;

	@Value("${app.bootstrap.demo-user-password:password}")
	private String demoUserPassword;

	@Bean
	public CommandLineRunner initData(UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			RegisteredClientRepository registeredClientRepository) {
		return args -> {
			initUsers(userRepository, passwordEncoder);
			initClient(passwordEncoder, registeredClientRepository);
		};
	}

	private void initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		if (!seedDemoUsers) {
			log.info("[bootstrap] seed-demo-users=false, skip default users");
			return;
		}
		if (userRepository.count() > 0) {
			return;
		}
		userRepository.save(new User("admin", passwordEncoder.encode(demoUserPassword), "ROLE_ADMIN"));
		userRepository.save(new User("user", passwordEncoder.encode(demoUserPassword), "ROLE_USER"));
		// 默认口令是安全事故的常见源头，必须醒目提示
		log.warn("[bootstrap] 已创建默认演示账号 admin / user，口令取自 app.bootstrap.demo-user-password。"
				+ "生产环境请立即改密，或设置 app.bootstrap.seed-demo-users=false 关闭该行为。");
	}

	private void initClient(PasswordEncoder passwordEncoder,
			RegisteredClientRepository registeredClientRepository) {
		RegisteredClient existing = registeredClientRepository.findByClientId(clientId);
		if (existing != null) {
			// 已存在则不动：JdbcRegisteredClientRepository 没有 update 语义，
			// 拿新 UUID 重建会留下孤儿授权记录，交给管理后台修改更安全。
			log.info("[bootstrap] client already exists, skip: clientId={}", clientId);
			return;
		}

		RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId(clientId)
				.clientSecret(passwordEncoder.encode(clientSecret))
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.postLogoutRedirectUri(postLogoutRedirectUri)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.clientSettings(ClientSettings.builder()
						.requireAuthorizationConsent(true)
						.requireProofKey(true)
						.build())
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(accessTokenTtl)
						.build());
		// 逐个注册，修掉原先只取前两个、不足时又重复第一个的问题
		for (String uri : redirectUris) {
			if (uri != null && !uri.isBlank()) {
				builder = builder.redirectUri(uri.trim());
			}
		}
		registeredClientRepository.save(builder.build());
		log.info("[bootstrap] initialized default client: clientId={}, redirectUriCount={}",
				clientId, redirectUris.length);
	}
}
