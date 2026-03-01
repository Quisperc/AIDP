package cn.civer.client.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "auth-server-clients", url = "${app.auth-server-url:http://127.0.0.1:8080}")
public interface ClientFeignClient {

	@GetMapping("/api/clients")
	List<ClientSummaryDto> listClients();

	@GetMapping("/api/clients/{clientId}")
	ClientSummaryDto getClient(@PathVariable("clientId") String clientId);

	@PostMapping("/api/clients")
	String registerClient(@RequestBody ClientDto dto);

	@PutMapping("/api/clients/{clientId}")
	String updateClient(@PathVariable("clientId") String clientId, @RequestBody ClientDto dto);

	@DeleteMapping("/api/clients/{clientId}")
	String deleteClient(@PathVariable("clientId") String clientId);

	class ClientDto {
		public String clientId;
		public String clientSecret;
		public String redirectUri;
		public String postLogoutRedirectUri;
		public String clientName;
	}

	class ClientSummaryDto {
		public String id;
		public String clientId;
		public String clientName;
		public String redirectUri;
		public String postLogoutRedirectUri;
	}
}
