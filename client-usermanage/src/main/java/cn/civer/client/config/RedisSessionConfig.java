package cn.civer.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 显式启用 Redis 会话。
 *
 * Spring Boot 4 已移除 SessionAutoConfiguration —— 只加依赖、只配
 * spring.session.store-type=redis 不会自动创建 SessionRepository，
 * 结果是 SessionRegistryConfig 注入 FindByIndexNameSessionRepository 时
 * 直接 NoSuchBeanDefinition（测试部署时实际踩到）。
 *
 * 用 @EnableRedisIndexedHttpSession 而不是 @EnableRedisHttpSession：
 * 前者创建 RedisIndexedSessionRepository，实现了 FindByIndexNameSessionRepository，
 * SpringSessionBackedSessionRegistry 才能按用户名查到该用户在所有实例上的会话。
 * 代价是需要 Redis keyspace notifications（由配置里的 configure-action 处理）。
 *
 * 仅在 SESSION_STORE=redis 时生效，默认单机不加载。
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisIndexedHttpSession
public class RedisSessionConfig {

	@Value("${server.servlet.session.cookie.name:CLIENT_SESSIONID}")
	private String cookieName;

	@Value("${server.servlet.session.cookie.domain:}")
	private String cookieDomain;

	@Value("${app.cookie.same-site:Lax}")
	private String sameSite;

	@Value("${app.cookie.secure:false}")
	private boolean secure;

	/**
	 * 启用 Spring Session 后，会话 Cookie 由 CookieSerializer 生成，
	 * server.servlet.session.cookie.* 不再生效（实测 SameSite 一直是 Lax）。
	 *
	 * 前端在 EdgeOne Pages 独立域名时属于跨站，Lax 会让浏览器直接丢弃会话 Cookie，
	 * 表现为登录后立刻变回未登录。跨站必须 SameSite=None 且 Secure=true。
	 */
	@Bean
	public CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(cookieName);
		if (cookieDomain != null && !cookieDomain.isBlank()) {
			serializer.setDomainName(cookieDomain);
		}
		serializer.setSameSite(sameSite);
		serializer.setUseSecureCookie(secure || "None".equalsIgnoreCase(sameSite));
		return serializer;
	}
}
