package cn.civer.client.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * 声明式 HTTP 客户端：调用认证中心客户端管理 API（Spring Boot 4 HTTP Service Client）。
 */
@HttpExchange("/api")
public interface ClientServiceClient {

	@GetExchange("/clients")
	List<ClientSummaryDto> listClients();

	@GetExchange("/clients/{clientId}")
	ClientSummaryDto getClient(@PathVariable("clientId") String clientId);

	@PostExchange("/clients")
	String registerClient(@RequestBody ClientDto dto);

	@PutExchange("/clients/{clientId}")
	String updateClient(@PathVariable("clientId") String clientId, @RequestBody ClientDto dto);

	@DeleteExchange("/clients/{clientId}")
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
