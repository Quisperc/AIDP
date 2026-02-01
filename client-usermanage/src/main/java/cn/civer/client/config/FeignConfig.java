package cn.civer.client.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@Configuration
public class FeignConfig {

	@Bean
	public RequestInterceptor requestInterceptor(OAuth2AuthorizedClientService clientService) {
		return new RequestInterceptor() {
			@Override
			public void apply(RequestTemplate template) {
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				if (authentication instanceof OAuth2AuthenticationToken) {
					OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
					OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
							oauthToken.getAuthorizedClientRegistrationId(),
							oauthToken.getName());

					if (client != null && client.getAccessToken() != null) {
						template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
					}
				}
			}
		};
	}
}
