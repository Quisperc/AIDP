package cn.civer.client.config;

import cn.civer.client.client.ClientServiceClient;
import cn.civer.client.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 认证中心 HTTP 声明式客户端配置（Spring Boot 4 HTTP Service Client）。
 * 使用 RestClient + HttpServiceProxyFactory 替代 OpenFeign，并自动附加 OAuth2 Bearer 令牌。
 */
@Configuration
public class AuthServerHttpClientConfig {

	@Bean
	public RestClient authServerRestClient(
			@Value("${app.auth-server-url:http://127.0.0.1:8080}") String baseUrl,
			OAuth2AuthorizedClientService authorizedClientService) {
		String url = (baseUrl == null || baseUrl.isBlank()) ? "http://127.0.0.1:8080" : baseUrl.replaceAll("/$", "");
		ClientHttpRequestInterceptor oauth2Interceptor = (request, body, execution) -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
				OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
						oauthToken.getAuthorizedClientRegistrationId(),
						oauthToken.getName());
				if (client != null) {
					OAuth2AccessToken accessToken = client.getAccessToken();
					if (accessToken != null) {
						request.getHeaders().setBearerAuth(accessToken.getTokenValue());
					}
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
