package cn.civer.authserver.controller;

import cn.civer.authserver.service.SsoLogoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class RevocationController {

	private static final Logger log = LoggerFactory.getLogger(RevocationController.class);

	private final OAuth2AuthorizationConsentService authorizationConsentService;
	private final RegisteredClientRepository registeredClientRepository;
	private final SsoLogoutService ssoLogoutService;

	public RevocationController(OAuth2AuthorizationConsentService authorizationConsentService,
			RegisteredClientRepository registeredClientRepository,
			SsoLogoutService ssoLogoutService) {
		this.authorizationConsentService = authorizationConsentService;
		this.registeredClientRepository = registeredClientRepository;
		this.ssoLogoutService = ssoLogoutService;
	}

	@GetMapping("/oauth2/revoke-consent")
	public String revokeConsent(Principal principal,
			@RequestParam("client_id") String clientId,
			@RequestParam(value = "redirect_uri", required = false, defaultValue = "/") String redirectUri) {

		log.debug("[revoke-consent] clientId={}, principal={}", clientId,
				principal != null ? principal.getName() : "NULL");

		if (principal != null) {
			// Find the Client to get its UUID
			RegisteredClient client = registeredClientRepository.findByClientId(clientId);

			if (client != null) {
				String clientUuid = client.getId();
				// Use the UUID to find the consent
				OAuth2AuthorizationConsent consent = authorizationConsentService.findById(clientUuid,
						principal.getName());

				if (consent != null) {
					authorizationConsentService.remove(consent);
					log.info("[revoke-consent] revoked: user={}, clientId={}", principal.getName(), clientId);
				} else {
					log.debug("[revoke-consent] no consent to revoke: user={}, clientId={}",
							principal.getName(), clientId);
				}
			} else {
				log.warn("[revoke-consent] client not found: clientId={}", clientId);
			}
		} else {
			log.warn("[revoke-consent] unauthenticated request, skip: clientId={}", clientId);
		}

		// 跳转地址必须校验，否则构造成 /oauth2/revoke-consent?redirect_uri=https://evil.com
		// 即可借本站域名做钓鱼跳转（开放重定向）。
		return "redirect:" + safeRedirect(redirectUri);
	}

	/**
	 * 将 redirect_uri 收敛到安全范围：
	 * 1. 站内相对路径（以单个 / 开头）—— 直接放行；
	 *    //evil.com 这类「协议相对 URL」是开放重定向的经典绕过写法，必须排除。
	 * 2. 绝对 URL —— 必须命中已注册客户端 redirect_uris 的 host 白名单。
	 * 3. 其余一律回落到首页。
	 */
	private String safeRedirect(String redirectUri) {
		if (redirectUri == null || redirectUri.isBlank()) {
			return "/";
		}
		String value = redirectUri.trim();
		if (value.startsWith("/") && !value.startsWith("//")) {
			return value;
		}
		if (ssoLogoutService.isAllowedLogoutRedirect(value)) {
			return value;
		}
		log.warn("[revoke-consent] blocked unsafe redirect_uri={}, fallback to /", value);
		return "/";
	}
}
