package cn.civer.authserver.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway 迁移配置。
 *
 * 注意：Spring Boot 4 已移除 FlywayAutoConfiguration —— 只引入 flyway-core 依赖
 * 并不会自动执行迁移（jar 里已无该自动配置类）。这里显式接管：
 * 用 initMethod="migrate" 在 bean 初始化时执行，并让依赖数据库的
 * TokenConfig 通过 @DependsOn("flyway") 排在其后。
 *
 * baseline-on-migrate=true 用于兼容历史库：表早已存在的库会自动打基线并跳过
 * 早期迁移，不会因 CREATE TABLE 冲突而启动失败。
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

	private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

	@Bean(initMethod = "migrate")
	public Flyway flyway(DataSource dataSource,
			@org.springframework.beans.factory.annotation.Value("${spring.flyway.locations:classpath:db/migration}")
			String locations) {
		log.info("[flyway] migrating from {}", locations);
		return Flyway.configure()
				.dataSource(dataSource)
				.locations(locations)
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.failOnMissingLocations(true)
				.load();
	}
}
