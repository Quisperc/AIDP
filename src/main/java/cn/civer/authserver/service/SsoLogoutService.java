package cn.civer.authserver.service;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SSO 全局退出：清除该用户的授权同意并向所有客户端发送 OIDC Back-Channel Logout。
 * 可供「用户主动登出」与「修改账号/密码成功后」等场景复用。
 */
@Service
public class SsoLogoutService {

	private static final Logger log = LoggerFactory.getLogger(SsoLogoutService.class);

	private static final String HOST_CACHE = "ssoAllowedHosts";

	private final JdbcTemplate jdbcTemplate;
	private final AuthorizationServerSettings authorizationServerSettings;
	private final JwtEncoder jwtEncoder;
	/*
	 * 用 CacheManager 手动读写，而不是 @Cacheable：
	 * isAllowedLogoutRedirect() -> allowedHosts() 是同一个类里的内部调用，
	 * 注解走 Spring AOP 代理会静默不生效。手动调用不受代理限制。
	 * 单机时是 ConcurrentMap（CACHE_TYPE=simple），多实例配 Redis 后
	 * 变成共享缓存，任一实例 invalidate 都会让其它实例立即失效。
	 */
	private final org.springframework.cache.CacheManager cacheManager;

	/** 广播用 HTTP 客户端：复用连接与线程池，避免每次登出新建且无超时地挂起 */
	private final HttpClient httpClient;
	private final ExecutorService broadcastExecutor;

	/** 客户端 logout 端点路径，各子系统需暴露该端点接收 logout_token */
	private final String clientLogoutPath;

	public SsoLogoutService(JdbcTemplate jdbcTemplate,
			JWKSource<SecurityContext> jwkSource,
			AuthorizationServerSettings authorizationServerSettings,
			org.springframework.cache.CacheManager cacheManager,
			@Value("${app.sso.client-logout-path:/api/sso-logout}") String clientLogoutPath) {
		this.jdbcTemplate = jdbcTemplate;
		this.authorizationServerSettings = authorizationServerSettings;
		this.cacheManager = cacheManager;
		this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
		this.clientLogoutPath = clientLogoutPath.startsWith("/") ? clientLogoutPath : "/" + clientLogoutPath;
		this.broadcastExecutor = Executors.newFixedThreadPool(4, runnable -> {
			Thread t = new Thread(runnable, "sso-logout-broadcast");
			t.setDaemon(true);
			return t;
		});
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.executor(broadcastExecutor)
				.build();
	}

	@PreDestroy
	void shutdown() {
		broadcastExecutor.shutdown();
		try {
			if (!broadcastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				broadcastExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			broadcastExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 执行全局退出：清除授权同意并向后端通知所有客户端退出该用户会话。
	 *
	 * @param principalName 当前主体名（一般为用户名，与 oauth2_authorization_consent.principal_name 一致）
	 * @param issuer        签发者 URL，若为 null 则使用 AuthorizationServerSettings 中的配置
	 */
	public void performGlobalLogout(String principalName, String issuer) {
		if (principalName == null || principalName.isBlank()) {
			return;
		}
		String sqlDeleteConsent = "DELETE FROM oauth2_authorization_consent WHERE principal_name = ?";
		jdbcTemplate.update(sqlDeleteConsent, principalName);
		log.debug("[sso-logout] cleared consents for user={}", principalName);

		String resolvedIssuer = (issuer != null && !issuer.isBlank())
				? issuer
				: authorizationServerSettings.getIssuer();
		if (resolvedIssuer == null) {
			log.warn("[sso-logout] no issuer configured, skip broadcast for user={}", principalName);
			return;
		}
		broadcastBackchannelLogout(principalName, resolvedIssuer);
	}

	/**
	 * 仅根据配置的 issuer 执行全局退出（适用于无 HttpServletRequest 的调用，如修改用户信息成功后）。
	 */
	public void performGlobalLogout(String principalName) {
		performGlobalLogout(principalName, null);
	}

	/**
	 * 校验 redirect_uri 是否允许（仅允许已注册客户端 redirect_uris 所在 host 的 URL），用于 logout 后跳回指定客户端。
	 */
	public boolean isAllowedLogoutRedirect(String redirectUri) {
		try {
			URI uri = new URI(redirectUri.trim());
			String scheme = uri.getScheme();
			if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
				return false;
			}
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return false;
			}
			return allowedHosts().contains(host.toLowerCase());
		} catch (Exception e) {
			return false;
		}
	}

	/** 客户端注册信息变更后调用，让 host 白名单缓存立即失效（Redis 模式下对所有实例生效） */
	public void invalidateHostCache() {
		org.springframework.cache.Cache cache = cacheManager.getCache(HOST_CACHE);
		if (cache != null) {
			cache.clear();
		}
	}

	/**
	 * 解析 issuer：优先使用配置，否则从 request 推导（供 Handler 等有请求上下文的场景使用）。
	 */
	public String getResolvedIssuer(HttpServletRequest request) {
		String issuer = authorizationServerSettings.getIssuer();
		if (issuer != null && !issuer.isBlank()) {
			return issuer;
		}
		if (request != null) {
			issuer = request.getScheme() + "://" + request.getServerName();
			if (("http".equals(request.getScheme()) && request.getServerPort() != 80) ||
					("https".equals(request.getScheme()) && request.getServerPort() != 443)) {
				issuer += ":" + request.getServerPort();
			}
			return issuer;
		}
		return null;
	}

	private void broadcastBackchannelLogout(String principalName, String issuer) {
		List<Map<String, Object>> clientRows;
		try {
			clientRows = jdbcTemplate.queryForList("SELECT client_id, redirect_uris FROM oauth2_registered_client");
		} catch (Exception e) {
			log.error("[sso-logout] failed to load registered clients, skip broadcast", e);
			return;
		}

		Set<String> processed = new HashSet<>();
		for (Map<String, Object> row : clientRows) {
			String clientId = (String) row.get("client_id");
			String blobs = (String) row.get("redirect_uris");
			if (blobs == null) {
				continue;
			}
			for (String uri : blobs.split(",")) {
				if (uri.isBlank()) {
					continue;
				}
				try {
					URI u = new URI(uri.trim());
					String logoutUrl = u.getScheme() + "://" + u.getHost()
							+ (u.getPort() != -1 ? ":" + u.getPort() : "") + clientLogoutPath;
					String key = clientId + "|" + logoutUrl;
					if (!processed.add(key)) {
						continue;
					}
					try {
						String logoutToken = generateLogoutToken(principalName, clientId, issuer);
						sendLogoutToken(logoutUrl, logoutToken);
					} catch (Exception e) {
						log.warn("[sso-logout] broadcast failed: url={}, reason={}", logoutUrl, e.getMessage());
					}
				} catch (Exception ignored) {
					// redirect_uri 非法，跳过该条
				}
			}
		}
	}

	private String generateLogoutToken(String principalName, String audience, String issuer) {
		JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(principalName)
				.audience(Collections.singletonList(audience))
				.issuedAt(Instant.now())
				.id(UUID.randomUUID().toString())
				.claim("events",
						Collections.singletonMap("http://schemas.openid.net/event/backchannel-logout",
								new HashMap<>()))
				.claim("sid", principalName)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
	}

	private void sendLogoutToken(String url, String token) {
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(5))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("logout_token=" + token))
				.build();
		httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
				.thenAccept(res -> log.debug("[sso-logout] token delivered: url={}, status={}", url, res.statusCode()))
				.exceptionally(ex -> {
					log.warn("[sso-logout] token delivery failed: url={}, reason={}", url, ex.getMessage());
					return null;
				});
	}

	/** host 白名单：登出是高频操作，不能每次都全表扫描并重新解析 */
	@SuppressWarnings("unchecked")
	private Set<String> allowedHosts() {
		org.springframework.cache.Cache cache = cacheManager.getCache(HOST_CACHE);
		if (cache != null) {
			Set<String> cached = cache.get("hosts", Set.class);
			if (cached != null) {
				return cached;
			}
		}
		Set<String> hosts = loadAllowedHosts();
		if (cache != null) {
			cache.put("hosts", hosts);
		}
		return hosts;
	}

	private Set<String> loadAllowedHosts() {
		Set<String> hosts = new HashSet<>();
		for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT redirect_uris FROM oauth2_registered_client")) {
			Object val = row.get("redirect_uris");
			if (val == null) {
				continue;
			}
			for (String one : val.toString().split(",")) {
				if (one.isBlank()) {
					continue;
				}
				try {
					URI u = new URI(one.trim());
					if (u.getHost() != null) {
						hosts.add(u.getHost().toLowerCase());
					}
				} catch (Exception ignored) {
					// 忽略非法 URI
				}
			}
		}
		return hosts;
	}
}
