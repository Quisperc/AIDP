package cn.civer.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 把 OAuth2 授权客户端（access/refresh token）存到 Redis，跨重启存活。
 *
 * 问题背景：Spring Security 对「已认证」用户默认用应用级的
 * InMemoryOAuth2AuthorizedClientService 保存 authorized client，它不随会话持久化。
 * 一旦把 Session 放进 Redis 而 token 还在应用内存里，重启应用后用户看起来仍登录着
 * （Redis 里的 SPRING_SECURITY_CONTEXT 还在），但转发调用认证中心时取不到 token，
 * 得到 401 并冒泡成 500。
 *
 * 这里只把字段拆成字符串存 Redis Hash，不序列化整个对象图，避免 JDK 序列化风险；
 * 读取时用 ClientRegistrationRepository 重新组装 OAuth2AuthorizedClient。
 *
 * 仅在 SESSION_STORE=redis 时启用；单机模式沿用 Spring Boot 默认的 InMemory 实现。
 */
@Component
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

	private static final String PREFIX = "aidp:oauth2:authorized:";
	private static final Duration TTL = Duration.ofDays(7);

	private final StringRedisTemplate redis;
	private final ClientRegistrationRepository registrations;

	public RedisOAuth2AuthorizedClientService(StringRedisTemplate redis,
			ClientRegistrationRepository registrations) {
		this.redis = redis;
		this.registrations = registrations;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
			String clientRegistrationId, String principalName) {
		String key = key(clientRegistrationId, principalName);
		Map<Object, Object> fields = redis.opsForHash().entries(key);
		if (fields.isEmpty()) {
			return null;
		}
		ClientRegistration registration = registrations.findByRegistrationId(clientRegistrationId);
		String accessTokenValue = str(fields.get("access_token"));
		if (registration == null || accessTokenValue == null) {
			return null;
		}
		Instant issuedAt = instant(fields.get("issued_at"), Instant.now());
		Instant expiresAt = instant(fields.get("expires_at"), Instant.now().plusSeconds(3600));
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
				OAuth2AccessToken.TokenType.BEARER, accessTokenValue, issuedAt, expiresAt);

		OAuth2RefreshToken refreshToken = null;
		String refreshValue = str(fields.get("refresh_token"));
		if (refreshValue != null) {
			refreshToken = new OAuth2RefreshToken(refreshValue,
					instant(fields.get("refresh_issued_at"), issuedAt));
		}
		// Spring Security 7 起 OAuth2AuthorizedClient 已是具体类（不再有 Default 实现）
		return (T) new OAuth2AuthorizedClient(
				registration, principalName, accessToken, refreshToken);
	}

	@Override
	public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
		String regId = authorizedClient.getClientRegistration().getRegistrationId();
		String key = key(regId, principal.getName());
		OAuth2AccessToken access = authorizedClient.getAccessToken();
		Map<String, String> fields = new HashMap<>();
		fields.put("access_token", access.getTokenValue());
		fields.put("issued_at", iso(access.getIssuedAt()));
		fields.put("expires_at", iso(access.getExpiresAt()));
		OAuth2RefreshToken refresh = authorizedClient.getRefreshToken();
		if (refresh != null) {
			fields.put("refresh_token", refresh.getTokenValue());
			fields.put("refresh_issued_at", iso(refresh.getIssuedAt()));
		} else {
			fields.put("refresh_token", "");
			fields.put("refresh_issued_at", "");
		}
		redis.opsForHash().putAll(key, fields);
		redis.expire(key, TTL);
	}

	@Override
	public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
		redis.delete(key(clientRegistrationId, principalName));
	}

	private static String key(String regId, String principal) {
		return PREFIX + regId + ":" + principal;
	}

	private static String str(Object v) {
		if (v == null) {
			return null;
		}
		String s = v.toString();
		return s.isEmpty() ? null : s;
	}

	private static String iso(Instant instant) {
		return instant == null ? Instant.now().toString() : instant.toString();
	}

	private static Instant instant(Object v, Instant fallback) {
		String s = str(v);
		if (s == null) {
			return fallback;
		}
		try {
			return Instant.parse(s);
		} catch (Exception e) {
			return fallback;
		}
	}
}
