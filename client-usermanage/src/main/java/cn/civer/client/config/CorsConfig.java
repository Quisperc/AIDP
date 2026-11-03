package cn.civer.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置：前端部署在 EdgeOne Pages（独立域名）时需要。
 *
 * 两个硬性约束：
 * 1. 允许凭证（Allow-Credentials=true）时，来源不能是通配 * —— 浏览器会直接拒绝，
 *    必须逐个列出可信来源，由 app.cors.allowed-origins 配置。
 * 2. 必须放行 X-XSRF-TOKEN 头，否则前端读到的 CSRF Token 传不过来，
 *    所有写操作都会被 Spring Security 拒绝。
 *
 * 未配置任何来源时不注册 CORS 规则，保持同源部署的原有行为。
 */
@Configuration
public class CorsConfig {

	@Value("${app.cors.allowed-origins:}")
	private List<String> allowedOrigins;

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		List<String> origins = allowedOrigins.stream()
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
		if (origins.isEmpty()) {
			// 同源部署：无需跨域规则
			return source;
		}
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Accept"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
