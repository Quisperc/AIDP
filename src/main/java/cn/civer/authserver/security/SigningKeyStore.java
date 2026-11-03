package cn.civer.authserver.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 签名密钥的持久化存储：启动时从 {@code oauth2_signing_key} 读取，缺失则生成并落库。
 *
 * 为什么必须落库：早先在内存随机生成，重启后已签发令牌全部验签失败（全员掉线），
 * 且多实例各持一把密钥、互相验不了对方的令牌，架构上只能单实例跑。
 *
 * 从 TokenConfig 抽出来的原因：那里一半是 JPA/JWK 装配、一半是 PEM 编解码与 SQL，
 * 两件事混在一个类里，换存储方式（例如改 KMS）要动装配代码，反之亦然。
 * 现在 TokenConfig 只负责「把存好的密钥装配成 JWKSource」。
 */
@Component
public class SigningKeyStore {

	private static final Logger log = LoggerFactory.getLogger(SigningKeyStore.class);

	private static final String SELECT_SQL =
			"SELECT key_id, public_key, private_key FROM oauth2_signing_key WHERE id = ?";
	private static final String INSERT_SQL =
			"INSERT INTO oauth2_signing_key (id, key_id, public_key, private_key) VALUES (?, ?, ?, ?)";

	private final JdbcTemplate jdbcTemplate;
	private final String recordId;

	public SigningKeyStore(JdbcTemplate jdbcTemplate,
			@Value("${app.signing-key.record-id:primary}") String recordId) {
		this.jdbcTemplate = jdbcTemplate;
		this.recordId = recordId;
	}

	/** 数据库里的一行：kid 与密钥对必须来自同一行，否则 JWKS 的 kid 与实际签名不符。 */
	public record SigningKey(String keyId, KeyPair keyPair) {
	}

	/**
	 * 读取已有密钥；不存在则生成并写入。
	 *
	 * 并发启动时可能两个实例都判定「不存在」，靠主键冲突让后到者落败，
	 * 再回读库里的那一份 —— 两边最终用同一把密钥，不会出现各自签一套的情况。
	 */
	public SigningKey loadOrCreate() {
		Row existing = find();
		if (existing != null) {
			return existing.toSigningKey();
		}

		SigningKey generated = new SigningKey(UUID.randomUUID().toString(), generateRsaKey());
		insert(generated);

		Row afterInsert = find();
		if (afterInsert != null && !afterInsert.keyId().equals(generated.keyId())) {
			log.info("[signing-key] another instance won the race, reusing keyId={}", afterInsert.keyId());
			return afterInsert.toSigningKey();
		}
		log.info("[signing-key] generated and persisted new RSA key: recordId={}, keyId={}",
				recordId, generated.keyId());
		return generated;
	}

	private Row find() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_SQL, recordId);
		if (rows.isEmpty()) {
			return null;
		}
		Map<String, Object> row = rows.get(0);
		return new Row((String) row.get("key_id"), (String) row.get("public_key"), (String) row.get("private_key"));
	}

	private void insert(SigningKey key) {
		try {
			jdbcTemplate.update(INSERT_SQL, recordId, key.keyId(),
					toPem("PUBLIC KEY", key.keyPair().getPublic().getEncoded()),
					toPem("PRIVATE KEY", key.keyPair().getPrivate().getEncoded()));
		} catch (DataIntegrityViolationException e) {
			// 另一个实例抢先写入，忽略即可，随后会回读库里的记录
			log.debug("[signing-key] concurrent insert detected, will reuse existing key");
		}
	}

	/** 库里的一行原始数据；解析失败直接抛出，便于人工介入。 */
	private record Row(String keyId, String publicPem, String privatePem) {

		SigningKey toSigningKey() {
			try {
				return new SigningKey(keyId, fromPem(publicPem, privatePem));
			} catch (Exception e) {
				// 密钥损坏时不能静默重建：重建会让已签发的令牌全部失效
				throw new IllegalStateException("持久化签名密钥无法解析，请检查 oauth2_signing_key 记录", e);
			}
		}
	}

	private static KeyPair generateRsaKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception ex) {
			throw new IllegalStateException("无法生成 RSA 签名密钥", ex);
		}
	}

	private static KeyPair fromPem(String publicPem, String privatePem) throws Exception {
		KeyFactory factory = KeyFactory.getInstance("RSA");
		PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(decodePem(publicPem)));
		PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem)));
		return new KeyPair(publicKey, privateKey);
	}

	private static byte[] decodePem(String pem) {
		return Base64.getDecoder().decode(pem
				.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", ""));
	}

	private static String toPem(String label, byte[] encoded) {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
		return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
	}
}
