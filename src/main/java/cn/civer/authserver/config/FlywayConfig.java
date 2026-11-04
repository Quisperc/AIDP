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
 * 之前 locations 从配置读、其余项硬编码在代码里，yml 里那几行像配置其实不生效，
 * 改配置的人会以为改动了实际行为。
 *
 * Dev 阶段所有库都由 Flyway 从零管理，baseline-on-migrate 默认关闭：
 * 不再兼容「有表但没有 flyway_schema_history」的历史库。
 *
 * 重要：db/migration 下**已应用过的脚本绝不可再修改**（哪怕只改注释）。
 * Flyway 会对已应用迁移做 checksum 校验，改动一个字符都会让启动直接失败；
 * 本地集成测试用的是全新 H2 库，暴露不了这个问题。需要修正时新增版本化迁移，
 * 或对目标库执行 flyway repair 显式对齐历史，并在发布后核对启动日志。
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

	private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

	@Bean(initMethod = "migrate")
	public Flyway flyway(DataSource dataSource,
			@Value("${spring.flyway.locations:classpath:db/migration}") String locations,
			@Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate,
			@Value("${spring.flyway.fail-on-missing-locations:true}") boolean failOnMissingLocations) {
		log.info("[flyway] migrating from {} (baselineOnMigrate={})", locations, baselineOnMigrate);
		return Flyway.configure()
				.dataSource(dataSource)
				.locations(locations)
				.baselineOnMigrate(baselineOnMigrate)
				.failOnMissingLocations(failOnMissingLocations)
				.load();
	}
}
