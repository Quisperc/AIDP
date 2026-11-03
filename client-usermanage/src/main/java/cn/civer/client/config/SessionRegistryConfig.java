package cn.civer.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/**
 * 会话注册表：决定 maximumSessions(1) 的作用范围。
 *
 * 这里有个容易漏掉的点：只把 HTTP Session 放进 Redis 还不够。
 * Spring Security 的并发会话控制读的是 SessionRegistry，
 * 如果它仍是内存实现，用户可以在实例 A 和实例 B 各登录一次，
 * 「单设备登录」形同虚设。Redis 模式下必须换成
 * SpringSessionBackedSessionRegistry，让它按 principal 去 Redis 里查所有会话。
 *
 * 两种实现互斥，由 spring.session.store-type 决定，默认单机无需 Redis。
 */
@Configuration
public class SessionRegistryConfig {

	@Bean
	@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none", matchIfMissing = true)
	public SessionRegistry localSessionRegistry() {
		return new SessionRegistryImpl();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
	public SessionRegistry redisSessionRegistry(
			FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
		return new SpringSessionBackedSessionRegistry<>(sessionRepository);
	}
}
