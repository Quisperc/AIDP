package cn.civer.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * OAuth2 授权服务器的持久化组件。
 *
 * 这些 bean 只回答「授权数据存在哪」，与「谁能访问什么」无关，
 * 因此从安全过滤器链的配置类里拆出来：改存储（换实现、加缓存）不必动安全规则，
 * 改安全规则也不会碰到协议存储。
 *
 * 三张表由 Flyway 的 V1 迁移建立，ddl-auto 固定为 none。
 */
@Configuration
public class OAuth2PersistenceConfig {

	@Bean
	public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
		return new JdbcRegisteredClientRepository(jdbcTemplate);
	}

	@Bean
	public OAuth2AuthorizationConsentService authorizationConsentService(
			JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
	}

	@Bean
	public OAuth2AuthorizationService authorizationService(
			JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
	}
}
