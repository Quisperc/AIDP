package cn.civer.client.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-server-clients", url = "${app.auth-server-url:http://127.0.0.1:8080}")
public interface ClientFeignClient {

	@PostMapping("/api/clients")
	String registerClient(@RequestBody ClientDto dto);

	// Simple DTO inner class or could be shared
	class ClientDto {
		public String clientId;
		public String clientSecret;
		public String redirectUri;
		public String postLogoutRedirectUri;
		public String clientName;

		// Getters and Setters or Public Fields
	}
}
