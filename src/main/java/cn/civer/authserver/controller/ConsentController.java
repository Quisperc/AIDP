package cn.civer.authserver.controller;

import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Controller
public class ConsentController {

	private final RegisteredClientRepository registeredClientRepository;

	public ConsentController(RegisteredClientRepository registeredClientRepository) {
		this.registeredClientRepository = registeredClientRepository;
	}

	@GetMapping(value = "/oauth2/consent")
	public String consent(Principal principal, Model model,
			@RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
			@RequestParam(OAuth2ParameterNames.SCOPE) String scope,
			@RequestParam(OAuth2ParameterNames.STATE) String state) {

		// Remove scopes that were already approved
		Set<String> scopesToApprove = new HashSet<>();
		Set<String> previouslyApprovedScopes = new HashSet<>();
		RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);

		if (registeredClient == null) {
			throw new IllegalArgumentException("Invalid client id: " + clientId);
		}

		for (String unauthorizedScope : StringUtils.delimitedListToStringArray(scope, " ")) {
			// In a real app, you might check if they already consented to some scopes.
			// For now, we show all requested scopes.
			scopesToApprove.add(unauthorizedScope);
		}

		model.addAttribute("clientId", clientId);
		model.addAttribute("state", state);
		model.addAttribute("scopes", scopesToApprove);
		model.addAttribute("previouslyApprovedScopes", previouslyApprovedScopes);
		model.addAttribute("principalName", principal.getName());
		model.addAttribute("clientName", registeredClient.getClientName() != null ? registeredClient.getClientName()
				: registeredClient.getClientId());

		return "consent";
	}
}
