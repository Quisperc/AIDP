package cn.civer.client.config;

import cn.civer.client.client.ClientServiceClient;
import cn.civer.client.client.UserServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 认证中心 HTTP 声明式客户端配置（Spring Boot 4 HTTP Service Client）。
 *
 * 这里有个容易踩的坑：OAuth2 登录成功后，access token 存在 HttpSession 的
 * OAuth2AuthorizedClientRepository 里，而不是应用级的 OAuth2AuthorizedClientService
 * （后者默认 InMemory，重启即丢，且不会自动刷新）。
 * 之前用 service.loadAuthorizedClient() 取令牌，重启后取到 null，转发请求不带
 * Authorization 头，认证中心返回 401，异常冒泡成前端看到的 500。
 *
 * 改用 OAuth2AuthorizedClientManager.authorize()：
 * - 从当前会话的 repository 加载；
 * - token 过期时自动用 refresh_token 换新；
 * - 换完自动回写会话。
 * 它依赖当前 Servlet 请求，因此用 RequestContextHolder 取 request/response。
 *
 * baseUrl 单独用 app.auth-server-api-url：生产可指向容器内网地址，
 * 避免转发调用绕一圈公网 CDN（CDN 可能剥离 Authorization 头）。
 * issuer-uri 仍用 app.auth-server-url 的公网地址，两者不能混。
 */
@Configuration
public class AuthServerHttpClientConfig {

	private static final Logger log = LoggerFactory.getLogger(AuthServerHttpClientConfig.class);

	@Bean
	public RestClient authServerRestClient(
			@Value("${app.auth-server-api-url:${app.auth-server-url:http://127.0.0.1:8080}}") String baseUrl,
			OAuth2AuthorizedClientManager authorizedClientManager) {
		String url = (baseUrl == null || baseUrl.isBlank())
				? "http://127.0.0.1:8080"
				: baseUrl.replaceAll("/$", "");

		ClientHttpRequestInterceptor oauth2Interceptor = (request, body, execution) -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication instanceof OAuth2AuthenticationToken oauthToken
					&& RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
				HttpServletRequest httpRequest = attrs.getRequest();
				HttpServletResponse httpResponse = attrs.getResponse();
				try {
					OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
							.withClientRegistrationId(oauthToken.getAuthorizedClientRegistrationId())
							.principal(oauthToken)
							.attribute(HttpServletRequest.class.getName(), httpRequest)
							.attribute(HttpServletResponse.class.getName(), httpResponse)
							.build();
					OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);
					if (client != null && client.getAccessToken() != null) {
						request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
					} else {
						log.warn("[auth-api] no authorized client/token available for {}", oauthToken.getName());
					}
				} catch (Exception e) {
					// 不要因为取令牌失败就把整个请求打断成 500，交给下游返回 401，
					// 由全局异常处理映射成可读错误
					log.warn("[auth-api] failed to obtain access token: {}", e.getMessage());
				}
			}
			return execution.execute(request, body);
		};

		return RestClient.builder()
				.baseUrl(url)
				.requestInterceptor(oauth2Interceptor)
				.build();
	}

	@Bean
	public HttpServiceProxyFactory authServerHttpServiceProxyFactory(RestClient authServerRestClient) {
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(authServerRestClient))
				.build();
	}

	@Bean
	public UserServiceClient userServiceClient(HttpServiceProxyFactory authServerHttpServiceProxyFactory) {
		return authServerHttpServiceProxyFactory.createClient(UserServiceClient.class);
	}

	@Bean
	public ClientServiceClient clientServiceClient(HttpServiceProxyFactory authServerHttpServiceProxyFactory) {
		return authServerHttpServiceProxyFactory.createClient(ClientServiceClient.class);
	}
}
