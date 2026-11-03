package cn.civer.authserver.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 启用缓存基础设施。
 *
 * Spring Boot 4 的 CacheAutoConfiguration 只有在检测到 @EnableCaching 引入的
 * CacheAspectSupport 之后才会创建 CacheManager；只加 spring-boot-starter-cache
 * 依赖、不写这个注解，注入 CacheManager 时会直接报 NoSuchBeanDefinition。
 *
 * 具体用哪种实现由 spring.cache.type 决定：
 * - simple（默认）：ConcurrentMapCacheManager，单机进程内缓存；
 * - redis：RedisCacheManager，多实例共享，任一实例清缓存其它实例立即可见。
 * 这里不硬编码实现，切换只需环境变量 CACHE_TYPE。
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
