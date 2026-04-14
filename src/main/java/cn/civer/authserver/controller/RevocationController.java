package cn.civer.authserver.controller;

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

	public RevocationController(OAuth2AuthorizationConsentService authorizationConsentService,
			RegisteredClientRepository registeredClientRepository) {
		this.authorizationConsentService = authorizationConsentService;
		this.registeredClientRepository = registeredClientRepository;
	}

	@GetMapping("/oauth2/revoke-consent")
	public String revokeConsent(Principal principal,
			@RequestParam("client_id") String clientId,
			@RequestParam(value = "redirect_uri", required = false, defaultValue = "/") String redirectUri) {

		log.info("[revoke-consent] clientId={}, principal={}", clientId,
				principal != null ? principal.getName() : "NULL");

		if (principal != null) {
			RegisteredClient client = registeredClientRepository.findByClientId(clientId);

			if (client != null) {
				String clientUuid = client.getId();
				OAuth2AuthorizationConsent consent = authorizationConsentService.findById(clientUuid,
						principal.getName());

				if (consent != null) {
					authorizationConsentService.remove(consent);
					log.info("[revoke-consent] Revoked consent for user '{}' and client '{}' (UUID: {})",
							principal.getName(), clientId, clientUuid);
				} else {
					log.warn("[revoke-consent] No consent found for user '{}' and client '{}' (UUID: {})",
							principal.getName(), clientId, clientUuid);
				}
			} else {
				log.warn("[revoke-consent] Client not found: {}", clientId);
			}
		} else {
			log.warn("[revoke-consent] Cannot revoke consent - user is not authenticated");
		}

		return "redirect:" + redirectUri;
	}
}
