package cn.civer.authserver.controller;

import cn.civer.authserver.dto.ClientDto;
import cn.civer.authserver.dto.ClientSummaryDto;
import cn.civer.authserver.service.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户端管理接口。
 *
 * 业务逻辑已全部移到 {@link ClientService}，本类只负责入参与响应；
 * 业务规则违反时由 Service 抛 BusinessException，交给 GlobalExceptionHandler 统一映射。
 */
@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RegisteredClientController {

	private static final Logger log = LoggerFactory.getLogger(RegisteredClientController.class);

	private final ClientService clientService;

	public RegisteredClientController(ClientService clientService) {
		this.clientService = clientService;
	}

	@GetMapping
	public List<ClientSummaryDto> listClients() {
		log.debug("[clients] list");
		return clientService.listClients();
	}

	@GetMapping("/{clientId}")
	public ClientSummaryDto getClient(@PathVariable String clientId) {
		log.debug("[clients] get clientId={}", clientId);
		return clientService.getClient(clientId);
	}

	@PostMapping
	public ResponseEntity<Void> registerClient(@RequestBody ClientDto dto) {
		log.debug("[clients] register clientId={}", dto.clientId());
		clientService.register(dto);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/{clientId}")
	public ResponseEntity<Void> updateClient(@PathVariable String clientId, @RequestBody ClientDto dto) {
		log.debug("[clients] update clientId={}", clientId);
		clientService.update(clientId, dto);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{clientId}")
	public ResponseEntity<Void> deleteClient(@PathVariable String clientId) {
		log.debug("[clients] delete clientId={}", clientId);
		clientService.delete(clientId);
		return ResponseEntity.ok().build();
	}
}
