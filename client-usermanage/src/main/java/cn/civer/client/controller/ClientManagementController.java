package cn.civer.client.controller;

import cn.civer.client.client.ClientServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户端管理 REST 接口（管理后台 SPA 的数据源）。
 *
 * 本服务不直接持有客户端数据，统一转发认证中心的 /api/clients。
 * 原先这里把认证中心的异常翻成中文再塞进 Model；改成分离后，认证中心返回
 * 标准 ApiError，由前端直接展示 message，异常翻译只需做一次。
 */
@RestController
@RequestMapping("/admin/clients")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ClientManagementController {

	private final ClientServiceClient clientServiceClient;

	public ClientManagementController(ClientServiceClient clientServiceClient) {
		this.clientServiceClient = clientServiceClient;
	}

	@GetMapping
	public List<ClientServiceClient.ClientSummaryDto> listClients() {
		return clientServiceClient.listClients();
	}

	@GetMapping("/{clientId}")
	public ClientServiceClient.ClientSummaryDto getClient(@PathVariable String clientId) {
		return clientServiceClient.getClient(clientId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public void registerClient(@RequestBody ClientServiceClient.ClientDto dto) {
		clientServiceClient.registerClient(dto);
	}

	@PutMapping("/{clientId}")
	public void updateClient(@PathVariable String clientId, @RequestBody ClientServiceClient.ClientDto dto) {
		clientServiceClient.updateClient(clientId, dto);
	}

	@DeleteMapping("/{clientId}")
	public ResponseEntity<Void> deleteClient(@PathVariable String clientId) {
		clientServiceClient.deleteClient(clientId);
		return ResponseEntity.noContent().build();
	}
}
