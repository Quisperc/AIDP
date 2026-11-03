package cn.civer.authserver.config;

import cn.civer.authserver.security.SigningKeyStore;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 令牌相关装配：JWKSource、授权服务器设置、JWT 自定义 claim。
 *
 * 密钥的读写与 PEM 编解码已移到 {@link SigningKeyStore}，这里只做装配：
 * 取到密钥对后包成 JWKSource 交给 Spring Authorization Server。
 *
 * @DependsOn("flyway")：jwkSource 会查 oauth2_signing_key，必须等 Flyway 建好表。
 * Spring Boot 4 已无 FlywayAutoConfiguration，迁移由 FlywayConfig 显式接管，
 * 依赖顺序需要自己声明。
 */
@Configuration
@DependsOn("flyway")
public class TokenConfig {

	private final SigningKeyStore signingKeyStore;

	@Value("${spring.security.oauth2.authorizationserver.issuer:${app.base-url:}}")
	private String issuer;

	public TokenConfig(SigningKeyStore signingKeyStore) {
		this.signingKeyStore = signingKeyStore;
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		SigningKeyStore.SigningKey signingKey = signingKeyStore.loadOrCreate();
		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) signingKey.keyPair().getPublic())
				.privateKey((RSAPrivateKey) signingKey.keyPair().getPrivate())
				.keyID(signingKey.keyId())
				.build();
		return new ImmutableJWKSet<>(new JWKSet(rsaKey));
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		var builder = AuthorizationServerSettings.builder();
		if (issuer != null && !issuer.isBlank()) {
			builder.issuer(issuer);
		}
		return builder.build();
	}

	/**
	 * 把登录用户的权限写进 JWT 的 roles claim。
	 *
	 * 只有 UserDetails 分支：Spring Security 的 User 实现本身也是 UserDetails，
	 * 原先并列的第二个 instanceof 分支永远走不到，已删除。
	 */
	@Bean
	public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
		return context -> {
			if (context.getPrincipal().getPrincipal() instanceof UserDetails user) {
				Set<String> authorities = user.getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.collect(Collectors.toSet());
				context.getClaims().claim("roles", authorities);
			}
		};
	}
}
