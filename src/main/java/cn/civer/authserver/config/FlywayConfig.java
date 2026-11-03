package cn.civer.authserver.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway 迁移配置。
 *
 * Spring Boot 4 已移除 FlywayAutoConfiguration —— 只引入 flyway-core 依赖并不会自动执行迁移
 * （jar 里已无该自动配置类）。这里显式接管：用 initMethod="migrate" 在 bean 初始化时执行，
 * 并让依赖数据库的 TokenConfig 通过 @DependsOn("flyway") 排在其后。
 *
 * 参数全部从 spring.flyway.* 读，默认值与 application.yml 一致：
 * 之前 locations 从配置读、另外三项硬编码在代码里，yml 里那几行像配置其实不生效，
 * 改配置的人会以为改动了实际行为。
 *
 * baseline-on-migrate=true 用于兼容历史库：表早已存在的库会自动打基线并跳过早期迁移，
 * 不会因 CREATE TABLE 冲突而启动失败。
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

	private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

	@Bean(initMethod = "migrate")
	public Flyway flyway(DataSource dataSource,
			@Value("${spring.flyway.locations:classpath:db/migration}") String locations,
			@Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
			@Value("${spring.flyway.baseline-version:0}") String baselineVersion,
			@Value("${spring.flyway.fail-on-missing-locations:true}") boolean failOnMissingLocations) {
		log.info("[flyway] migrating from {} (baselineOnMigrate={}, baselineVersion={})",
				locations, baselineOnMigrate, baselineVersion);
		return Flyway.configure()
				.dataSource(dataSource)
				.locations(locations)
				.baselineOnMigrate(baselineOnMigrate)
				.baselineVersion(baselineVersion)
				.failOnMissingLocations(failOnMissingLocations)
				.load();
	}
}
