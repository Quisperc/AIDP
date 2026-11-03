package cn.civer.authserver.service;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SSO 全局退出：清除该用户的授权同意并向所有客户端发送 OIDC Back-Channel Logout。
 * 可供「用户主动登出」与「修改账号/密码成功后」等场景复用。
 *
 * 职责边界：本类管「谁该被登出、允许跳回哪里」，令牌签发与 HTTP 推送交给
 * {@link BackchannelLogoutSender}；两件事的失败模式完全不同（逻辑没触发 vs 推送没到达），
 * 混在一个类里排障时很难分层。
 */
@Service
public class SsoLogoutService {

	private static final Logger log = LoggerFactory.getLogger(SsoLogoutService.class);

	private static final String HOST_CACHE = "ssoAllowedHosts";
	private static final String HOST_CACHE_KEY = "hosts";

	private final JdbcTemplate jdbcTemplate;
	private final AuthorizationServerSettings authorizationServerSettings;
	private final BackchannelLogoutSender logoutSender;
	/*
	 * 用 CacheManager 手动读写，而不是 @Cacheable：
	 * isAllowedLogoutRedirect() -> allowedHosts() 是同一个类里的内部调用，
	 * 注解走 Spring AOP 代理会静默不生效。手动调用不受代理限制。
	 * 单机时是 ConcurrentMap（CACHE_TYPE=simple），多实例配 Redis 后
	 * 变成共享缓存，任一实例 invalidate 都会让其它实例立即失效。
	 */
	private final CacheManager cacheManager;

	public SsoLogoutService(JdbcTemplate jdbcTemplate,
			JWKSource<SecurityContext> jwkSource,
			AuthorizationServerSettings authorizationServerSettings,
			CacheManager cacheManager,
			@Value("${app.sso.client-logout-path:/api/sso-logout}") String clientLogoutPath) {
		this.jdbcTemplate = jdbcTemplate;
		this.authorizationServerSettings = authorizationServerSettings;
		this.cacheManager = cacheManager;
		this.logoutSender = new BackchannelLogoutSender(jwkSource, clientLogoutPath);
	}

	@PreDestroy
	void shutdown() {
		logoutSender.shutdown();
	}

	/**
	 * 执行全局退出：清除授权同意与已签发的令牌，并向后端通知所有客户端退出该用户会话。
	 *
	 * 两处删除缺一不可：
	 * - consent 决定下次是否重新弹同意页；
	 * - authorization 决定已发出的 access / refresh / id token 是否还认。
	 * 只删 consent 时，客户端手里的 refresh_token 仍能在有效期内静默续期，
	 * 用户看到「已退出全部系统」，实际只是没再弹同意页。
	 *
	 * @param principalName 当前主体名（一般为用户名，与 oauth2_authorization_consent.principal_name 一致）
	 * @param issuer        签发者 URL，若为 null 则使用 AuthorizationServerSettings 中的配置
	 */
	@Transactional
	public void performGlobalLogout(String principalName, String issuer) {
		if (!StringUtils.hasText(principalName)) {
			return;
		}
		int consents = jdbcTemplate.update(
				"DELETE FROM oauth2_authorization_consent WHERE principal_name = ?", principalName);
		int authorizations = jdbcTemplate.update(
				"DELETE FROM oauth2_authorization WHERE principal_name = ?", principalName);
		log.info("[sso-logout] revoked principal={}: consents={}, authorizations={}",
				principalName, consents, authorizations);

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
	@Transactional
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
		Cache cache = cacheManager.getCache(HOST_CACHE);
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
		List<RegisteredClientUris> clients;
		try {
			clients = loadRegisteredClientUris();
		} catch (Exception e) {
			log.error("[sso-logout] failed to load registered clients, skip broadcast", e);
			return;
		}

		// 同一个 host 可能出现在同一客户端的多个 redirect_uri 里，按 clientId|url 去重
		Set<String> processed = new HashSet<>();
		for (RegisteredClientUris client : clients) {
			for (String logoutUrl : client.logoutUrls()) {
				if (!processed.add(client.clientId() + "|" + logoutUrl)) {
					continue;
				}
				try {
					String logoutToken = logoutSender.generate(principalName, client.clientId(), issuer);
					logoutSender.send(logoutUrl, logoutToken);
				} catch (Exception e) {
					log.warn("[sso-logout] broadcast failed: url={}, reason={}", logoutUrl, e.getMessage());
				}
			}
		}
	}

	/** host 白名单：登出是高频操作，不能每次都全表扫描并重新解析 */
	@SuppressWarnings("unchecked")
	private Set<String> allowedHosts() {
		Cache cache = cacheManager.getCache(HOST_CACHE);
		if (cache != null) {
			Set<String> cached = cache.get(HOST_CACHE_KEY, Set.class);
			if (cached != null) {
				return cached;
			}
		}
		Set<String> hosts = loadRegisteredClientUris().stream()
				.flatMap(client -> client.uris().stream())
				.map(SsoLogoutService::hostOf)
				.filter(host -> host != null && !host.isBlank())
				.map(String::toLowerCase)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (cache != null) {
			cache.put(HOST_CACHE_KEY, hosts);
		}
		return hosts;
	}

	/**
	 * 读一次注册表，拿到「客户端 → 它声明的回调地址」。
	 *
	 * 广播与白名单都需要这份数据，原先各自写一遍 SQL、各自 split / new URI / 取 host，
	 * 一处改了另一处忘改就会出现「能跳转但不能广播」这类不对称问题。
	 */
	private List<RegisteredClientUris> loadRegisteredClientUris() {
		List<Map<String, Object>> rows = jdbcTemplate
				.queryForList("SELECT client_id, redirect_uris FROM oauth2_registered_client");
		List<RegisteredClientUris> clients = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			Object raw = row.get("redirect_uris");
			if (raw == null) {
				continue;
			}
			List<String> uris = new ArrayList<>();
			for (String one : raw.toString().split(",")) {
				if (!one.isBlank()) {
					uris.add(one.trim());
				}
			}
			clients.add(new RegisteredClientUris((String) row.get("client_id"), uris));
		}
		return clients;
	}

	private static String hostOf(String uri) {
		try {
			return new URI(uri).getHost();
		} catch (Exception e) {
			// 非法 URI（历史脏数据）不参与白名单
			return null;
		}
	}

	private record RegisteredClientUris(String clientId, List<String> uris) {

		/** 回调地址的 origin + 约定的登出路径，即后端登出推送的目标 */
		List<String> logoutUrls() {
			List<String> urls = new ArrayList<>(uris.size());
			for (String uri : uris) {
				try {
					URI u = new URI(uri);
					if (u.getScheme() == null || u.getHost() == null) {
						continue;
					}
					urls.add(u.getScheme() + "://" + u.getHost()
							+ (u.getPort() != -1 ? ":" + u.getPort() : ""));
				} catch (Exception ignored) {
					// 非法 redirect_uri，跳过该条
				}
			}
			return urls;
		}
	}
}
