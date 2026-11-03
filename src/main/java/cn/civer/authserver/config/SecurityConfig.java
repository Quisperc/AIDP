package cn.civer.authserver.config;

import cn.civer.authserver.handler.LoginSuccessHandler;
import cn.civer.authserver.handler.SsoLogoutSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 唯一的安全过滤器链 + 口令编码器。
 *
 * 管理后台并入本应用后，这里只有一种身份来源：**表单登录建立的会话**。
 * 原先的「资源服务器 + JWT 转换器」是为管理端远程调用 /api/** 服务的，
 * 现在调用发生在进程内，已经不需要，一并移除。
 *
 * 路由分三类：
 * - 匿名可达：静态资源、登录/错误页、健康检查；
 * - OIDC 协议端点：由 Spring Authorization Server 自己的配置接管（授权端点会跳登录）；
 * - /admin/**：必须是 ROLE_ADMIN，URL 粒度足够，控制器上不再逐个加 @PreAuthorize。
 *
 * 命名沿用「SecurityConfig」：这个类管的是认证/授权规则本身。
 * OAuth2 授权服务器的持久化组件（客户端注册表、授权、同意）属于另一层职责，
 * 已拆到 {@link OAuth2PersistenceConfig}。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final SsoLogoutSuccessHandler ssoLogoutSuccessHandler;
	private final LoginSuccessHandler loginSuccessHandler;

	public SecurityConfig(SsoLogoutSuccessHandler ssoLogoutSuccessHandler,
			LoginSuccessHandler loginSuccessHandler) {
		this.ssoLogoutSuccessHandler = ssoLogoutSuccessHandler;
		this.loginSuccessHandler = loginSuccessHandler;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						// 健康检查必须匿名可达，否则容器编排与发布流水线拿到 401/302
						.requestMatchers("/", "/login", "/error", "/favicon.ico", "/css/**", "/js/**", "/images/**",
								"/actuator/health", "/actuator/health/**").permitAll()
						// 管理台：会话 + 角色。URL 粒度足够，方法上加 @PreAuthorize 反而重复
						.requestMatchers("/admin", "/admin/**").hasAuthority("ROLE_ADMIN")
						.anyRequest().authenticated())
				.formLogin((form) -> form
						.loginPage("/login")
						.successHandler(loginSuccessHandler)
						.permitAll())
				.logout((logout) -> logout
						.logoutUrl("/logout")
						.logoutSuccessHandler(ssoLogoutSuccessHandler)
						.permitAll())
				.oauth2AuthorizationServer(authorizationServer -> authorizationServer
						.oidc(Customizer.withDefaults())
						.authorizationEndpoint(authorizationEndpoint ->
								authorizationEndpoint.consentPage("/oauth2/consent")));

		return http.build();
	}

	/**
	 * 口令编码器属于安全基础设施，放在安全配置里。
	 *
	 * 原先它挂在 CustomUserDetailsService 上（该类同时标了 @Service 与 @Configuration），
	 * 等于让一个业务组件生产基础设施 bean：注入 PasswordEncoder 的任何类都和
	 * 「用户查询服务」产生了隐式依赖，分层也就乱了。
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
