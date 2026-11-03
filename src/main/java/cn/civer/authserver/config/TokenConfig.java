package cn.civer.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
// jwkSource 会查 oauth2_signing_key，必须等 Flyway 把表建好。
// Spring Boot 4 已无 FlywayAutoConfiguration，迁移由 FlywayConfig 显式接管，
// 这里的依赖顺序需要自己声明。
@DependsOn("flyway")
public class TokenConfig {

	private static final Logger log = LoggerFactory.getLogger(TokenConfig.class);

	private final JdbcTemplate jdbcTemplate;
	private final String keyRecordId;

	@Value("${spring.security.oauth2.authorizationserver.issuer:${app.base-url:}}")
	private String issuer;

	public TokenConfig(JdbcTemplate jdbcTemplate,
			@Value("${app.signing-key.record-id:primary}") String keyRecordId) {
		this.jdbcTemplate = jdbcTemplate;
		this.keyRecordId = keyRecordId;
	}

	/**
	 * 签名密钥：优先复用库里已持久化的密钥对。
	 *
	 * 原先每次启动都在内存随机生成，带来两个硬伤：
	 * 1) 重启后所有已签发令牌立刻验签失败，等于全员掉线；
	 * 2) 多实例部署时各实例密钥不同，彼此签发的令牌互相验不了 —— 架构上锁死单实例。
	 * 落库后实例共享同一密钥，可随意重启与水平扩容。
	 */
	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		KeyPair keyPair = loadOrCreateKeyPair();
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(loadKeyId())
				.build();
		return new ImmutableJWKSet<>(new JWKSet(rsaKey));
	}

	private KeyPair loadOrCreateKeyPair() {
		Map<String, Object> row = findKeyRow();
		if (row != null) {
			try {
				return fromPem((String) row.get("public_key"), (String) row.get("private_key"));
			} catch (Exception e) {
				// 密钥损坏时不能静默重建（会让已签发令牌全部失效），直接失败便于人工介入
				throw new IllegalStateException("持久化签名密钥无法解析，请检查 oauth2_signing_key 记录", e);
			}
		}
		KeyPair generated = generateRsaKey();
		String keyId = UUID.randomUUID().toString();
		insertKey(keyId, generated);
		log.info("[signing-key] generated and persisted new RSA key: recordId={}, keyId={}", keyRecordId, keyId);
		// 并发启动时可能已被别的实例写入，此时以库里的为准
		Map<String, Object> existing = findKeyRow();
		if (existing != null && !keyId.equals(existing.get("key_id"))) {
			try {
				return fromPem((String) existing.get("public_key"), (String) existing.get("private_key"));
			} catch (Exception e) {
				throw new IllegalStateException("持久化签名密钥无法解析", e);
			}
		}
		return generated;
	}

	private Map<String, Object> findKeyRow() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT key_id, public_key, private_key FROM oauth2_signing_key WHERE id = ?", keyRecordId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	private void insertKey(String keyId, KeyPair keyPair) {
		try {
			jdbcTemplate.update(
					"INSERT INTO oauth2_signing_key (id, key_id, public_key, private_key) VALUES (?, ?, ?, ?)",
					keyRecordId, keyId,
					toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()),
					toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
		} catch (DataIntegrityViolationException e) {
			// 另一个实例抢先写入，忽略即可，随后会读取库里的记录
			log.debug("[signing-key] concurrent insert detected, will reuse existing key");
		}
	}

	private String loadKeyId() {
		Map<String, Object> row = findKeyRow();
		return row != null ? (String) row.get("key_id") : UUID.randomUUID().toString();
	}

	private static KeyPair generateRsaKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static KeyPair fromPem(String publicPem, String privatePem) throws Exception {
		KeyFactory factory = KeyFactory.getInstance("RSA");
		PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(decodePem(publicPem)));
		PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem)));
		return new KeyPair(publicKey, privateKey);
	}

	private static byte[] decodePem(String pem) {
		String body = pem
				.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(body);
	}

	private static String toPem(String label, byte[] encoded) {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
		return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
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
