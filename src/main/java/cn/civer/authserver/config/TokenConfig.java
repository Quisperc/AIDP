package cn.civer.authserver.config;

import cn.civer.authserver.security.AccountUserDetails;
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
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.util.StringUtils;

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
 * ID Token 的 claim 有两条硬规则：
 * 1. sub 必须是不可变的 public_id。OIDC Core 要求 sub 在 issuer 内
 *    locally unique 且 never reassigned；可变的 username 不能承担这个角色，
 *    否则客户端按 (iss, sub) 关联账号时，用户改一次名就会多出一个账号。
 * 2. 标准 claim 只在 ID Token、且只在该次授权确实包含对应 scope 时写入。
 *    框架的 DefaultOidcUserInfoMapper 会从 ID Token 的 claims 里按 scope
 *    过滤生成 UserInfo，所以这里写对，两处就自然一致，无需自定义 mapper。
 *    若无条件写 email，只拿到 openid/profile 的客户端也能从 id_token 读到邮箱。
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

	@Bean
	public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
		return context -> {
			Object principal = context.getPrincipal().getPrincipal();

			// roles 沿用原语义，对 access token 与 id token 都有效
			if (principal instanceof UserDetails user) {
				Set<String> authorities = user.getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.collect(Collectors.toSet());
				context.getClaims().claim("roles", authorities);
			}

			if (!(principal instanceof AccountUserDetails account)
					|| !OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
				return;
			}

			// 覆写框架默认填入的 username，改用永不变的主体标识
			if (StringUtils.hasText(account.getPublicId())) {
				context.getClaims().subject(account.getPublicId());
			}

			Set<String> scopes = context.getAuthorizedScopes();
			if (scopes == null) {
				return;
			}
			if (scopes.contains(OidcScopes.EMAIL) && StringUtils.hasText(account.getEmail())) {
				context.getClaims()
						.claim(StandardClaimNames.EMAIL, account.getEmail())
						// 自助填写的邮箱未经验证，绝不能声明为已验证：
						// 下游若拿 email_verified 做账号合并会造成账号接管。
						.claim(StandardClaimNames.EMAIL_VERIFIED, account.isEmailVerified());
			}
			if (scopes.contains(OidcScopes.PROFILE)) {
				context.getClaims()
						.claim(StandardClaimNames.NICKNAME, account.getDisplayName())
						.claim(StandardClaimNames.PREFERRED_USERNAME, account.getUsername());
			}
		};
	}
}
