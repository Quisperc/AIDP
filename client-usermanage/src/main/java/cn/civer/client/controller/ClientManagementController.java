package cn.civer.client.controller;

import cn.civer.client.client.ClientFeignClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/clients")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ClientManagementController {

	private final ClientFeignClient clientFeignClient;

	public ClientManagementController(ClientFeignClient clientFeignClient) {
		this.clientFeignClient = clientFeignClient;
	}

	@GetMapping
	public String listClients(Model model) {
		// Since we don't have a list API yet, we just show the add form
		return "clients";
	}

	@PostMapping
	public String addClient(@RequestParam String clientId,
			@RequestParam String clientSecret,
			@RequestParam String redirectUri,
			@RequestParam String postLogoutRedirectUri,
			@RequestParam String clientName,
			Model model) {
		ClientFeignClient.ClientDto dto = new ClientFeignClient.ClientDto();
		dto.clientId = clientId;
		dto.clientSecret = clientSecret;
		dto.redirectUri = redirectUri;
		dto.postLogoutRedirectUri = postLogoutRedirectUri;
		dto.clientName = clientName;

		try {
			clientFeignClient.registerClient(dto);
			model.addAttribute("message", "Client Registered Successfully!");
		} catch (Exception e) {
			model.addAttribute("error", "Error registering client: " + e.getMessage());
		}
		return "clients";
	}
}
