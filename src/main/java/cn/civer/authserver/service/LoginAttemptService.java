package cn.civer.authserver.service;

import cn.civer.authserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.Duration;

/**
 * 登录失败限流（按用户名计数，滑动窗口）。
 *
 * 为什么按用户名而不是按 IP：目标是阻止对**某个账号**的密码猜解；按 IP 会误伤
 * 同一出口 NAT 后的整栋办公室，也挡不住分布式撞库。
 *
 * 为什么管理员豁免：管理员被锁在门外时没有任何自助解锁入口（管理台本身就要登录），
 * 属于「防爆破」换来「运维事故」。管理员的风险改由强口令 + 审计日志覆盖，
 * 并且失败记录照样进 WARN 日志。
 *
 * 实现用 CacheManager 而不是本地 Map：多实例 + Redis 时计数天然共享，
 * 单机时退化成 ConcurrentMap，行为一致。窗口用「首次失败时间戳 + 计数」表达，
 * 因为 ConcurrentMapCache 不支持 TTL。
 */
@Service
public class LoginAttemptService {

	/** 缓存名，需与 spring.cache.cache-names 中声明的一致 */
	public static final String CACHE_NAME = "loginAttempts";

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
	private static final String ADMIN_ROLE = "ROLE_ADMIN";

	private final CacheManager cacheManager;
	private final UserRepository userRepository;
	private final int maxAttempts;
	private final Duration window;

	public LoginAttemptService(CacheManager cacheManager,
			UserRepository userRepository,
			@Value("${app.login-guard.max-attempts:10}") int maxAttempts,
			@Value("${app.login-guard.window:15m}") Duration window) {
		this.cacheManager = cacheManager;
		this.userRepository = userRepository;
		this.maxAttempts = maxAttempts;
		this.window = window;
	}

	/** 是否应拒绝本次登录尝试 */
	public boolean isBlocked(String username) {
		if (!StringUtils.hasText(username) || isAdmin(username)) {
			return false;
		}
		Attempt attempt = read(username);
		if (attempt == null) {
			return false;
		}
		if (expired(attempt)) {
			evict(username);
			return false;
		}
		return attempt.count() >= maxAttempts;
	}

	public void recordFailure(String username) {
		if (!StringUtils.hasText(username) || isAdmin(username)) {
			return;
		}
		Cache cache = cache();
		if (cache == null) {
			return;
		}
		Attempt current = read(username);
		Attempt next = (current == null || expired(current))
				? new Attempt(1, System.currentTimeMillis())
				: new Attempt(current.count() + 1, current.windowStartMillis());
		cache.put(username, next);
		if (next.count() == maxAttempts) {
			log.warn("[login-guard] username={} 连续失败 {} 次，已临时锁定 {} 分钟",
					username, next.count(), window.toMinutes());
		}
	}

	/** 登录成功后清零，避免「失败几次、成功一次、再失败几次」被累计误锁 */
	public void clear(String username) {
		if (StringUtils.hasText(username)) {
			evict(username);
		}
	}

	/**
	 * 只统计 BadCredentials：用户名不存在与密码错误都会走到这里，正是要防的猜解行为。
	 * 账号停用（DisabledException）不计入——那不是「有人在猜密码」。
	 */
	@EventListener
	public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
		recordFailure(event.getAuthentication().getName());
	}

	@EventListener
	public void onSuccess(AuthenticationSuccessEvent event) {
		clear(event.getAuthentication().getName());
	}

	private boolean isAdmin(String username) {
		return userRepository.findByUsername(username)
				.map(user -> ADMIN_ROLE.equals(user.getRole()))
				.orElse(false);
	}

	private boolean expired(Attempt attempt) {
		return System.currentTimeMillis() - attempt.windowStartMillis() > window.toMillis();
	}

	private Attempt read(String username) {
		Cache cache = cache();
		if (cache == null) {
			return null;
		}
		return cache.get(username, Attempt.class);
	}

	private void evict(String username) {
		Cache cache = cache();
		if (cache != null) {
			cache.evict(username);
		}
	}

	/** 缓存未配置时放行并告警一次，不能因为限流设施缺失而让所有人登录不了 */
	private Cache cache() {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache == null) {
			log.debug("[login-guard] 缓存 {} 不存在，跳过限流", CACHE_NAME);
		}
		return cache;
	}

	/** Redis 缓存需要 JDK 序列化，故实现 Serializable */
	public record Attempt(int count, long windowStartMillis) implements Serializable {
	}
}
