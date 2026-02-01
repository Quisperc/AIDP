package cn.civer.authserver.controller;

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

		System.out.println("Revocation Request Received for client: " + clientId + ", Principal: "
				+ (principal != null ? principal.getName() : "NULL"));

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
					System.out.println("SUCCESS: Revoked consent for user '" + principal.getName() +
							"' and client '" + clientId + "' (UUID: " + clientUuid + ")");
				} else {
					System.out.println("WARNING: No consent found to revoke for user '" + principal.getName() +
							"' and client '" + clientId + "' (UUID: " + clientUuid + ")");
				}
			} else {
				System.out.println("ERROR: Client not found: " + clientId);
			}
		} else {
			System.out.println("ERROR: Cannot revoke consent - User is not authenticated (Principal is null)");
		}

		return "redirect:" + redirectUri;
	}
}
