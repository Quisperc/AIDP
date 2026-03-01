package cn.civer.authserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// DTO for create/update request
class ClientDto {
	public String clientId;
	public String clientSecret;
	public String redirectUri;
	public String postLogoutRedirectUri;
	public String clientName;
}

// DTO for list item and get (no secret)
class ClientSummaryDto {
	public String id;
	public String clientId;
	public String clientName;
	public String redirectUri;
	public String postLogoutRedirectUri;
}

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RegisteredClientController {

	private static final Logger log = LoggerFactory.getLogger(RegisteredClientController.class);
	private final RegisteredClientRepository registeredClientRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;

	public RegisteredClientController(RegisteredClientRepository registeredClientRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate) {
		this.registeredClientRepository = registeredClientRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping
	public ResponseEntity<List<ClientSummaryDto>> listClients() {
		log.debug("[clients] list");
		String sql = "SELECT id, client_id, client_name, redirect_uris, post_logout_redirect_uris FROM oauth2_registered_client ORDER BY client_id";
		List<ClientSummaryDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
			ClientSummaryDto dto = new ClientSummaryDto();
			dto.id = rs.getString("id");
			dto.clientId = rs.getString("client_id");
			dto.clientName = rs.getString("client_name");
			dto.redirectUri = rs.getString("redirect_uris");
			dto.postLogoutRedirectUri = rs.getString("post_logout_redirect_uris");
			return dto;
		});
		return ResponseEntity.ok(list);
	}

	@GetMapping("/{clientId}")
	public ResponseEntity<ClientSummaryDto> getClient(@PathVariable String clientId) {
		log.debug("[clients] get clientId={}", clientId);
		RegisteredClient client = registeredClientRepository.findByClientId(clientId);
		if (client == null) {
			log.warn("[clients] get not found: clientId={}", clientId);
			return ResponseEntity.notFound().build();
		}
		ClientSummaryDto dto = new ClientSummaryDto();
		dto.id = client.getId();
		dto.clientId = client.getClientId();
		dto.clientName = client.getClientName();
		dto.redirectUri = client.getRedirectUris() != null && !client.getRedirectUris().isEmpty()
				? client.getRedirectUris().iterator().next() : null;
		dto.postLogoutRedirectUri = client.getPostLogoutRedirectUris() != null && !client.getPostLogoutRedirectUris().isEmpty()
				? client.getPostLogoutRedirectUris().iterator().next() : null;
		return ResponseEntity.ok(dto);
	}

	@PostMapping
	public ResponseEntity<String> registerClient(@RequestBody ClientDto dto) {
		log.info("[clients] register clientId={}", dto.clientId);
		if (registeredClientRepository.findByClientId(dto.clientId) != null) {
			log.warn("[clients] register duplicate: clientId={}", dto.clientId);
			return ResponseEntity.status(409).body("Client ID already exists: " + dto.clientId);
		}
		RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId(dto.clientId)
				.clientSecret(passwordEncoder.encode(dto.clientSecret))
				.clientName(dto.clientName)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUri(dto.redirectUri)
				.postLogoutRedirectUri(dto.postLogoutRedirectUri)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
				.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(30)).build())
				.build();

		registeredClientRepository.save(registeredClient);
		log.info("[clients] registered: clientId={}", dto.clientId);
		return ResponseEntity.ok("Client registered successfully: " + dto.clientId);
	}

	@PutMapping("/{clientId}")
	public ResponseEntity<String> updateClient(@PathVariable String clientId, @RequestBody ClientDto dto) {
		log.info("[clients] update clientId={}", clientId);
		RegisteredClient existing = registeredClientRepository.findByClientId(clientId);
		if (existing == null) {
			log.warn("[clients] update not found: clientId={}", clientId);
			return ResponseEntity.notFound().build();
		}
		String newSecret = (dto.clientSecret != null && !dto.clientSecret.isBlank())
				? passwordEncoder.encode(dto.clientSecret)
				: existing.getClientSecret();
		String redirectUri = (dto.redirectUri != null && !dto.redirectUri.isBlank()) ? dto.redirectUri
				: (existing.getRedirectUris().isEmpty() ? null : existing.getRedirectUris().iterator().next());
		String postLogoutUri = (dto.postLogoutRedirectUri != null && !dto.postLogoutRedirectUri.isBlank()) ? dto.postLogoutRedirectUri
				: (existing.getPostLogoutRedirectUris().isEmpty() ? null : existing.getPostLogoutRedirectUris().iterator().next());
		String clientName = (dto.clientName != null && !dto.clientName.isBlank()) ? dto.clientName : existing.getClientName();
		RegisteredClient updated = RegisteredClient.withId(existing.getId())
				.clientId(existing.getClientId())
				.clientSecret(newSecret)
				.clientName(clientName)
				.clientAuthenticationMethods(c -> c.addAll(existing.getClientAuthenticationMethods()))
				.authorizationGrantTypes(g -> g.addAll(existing.getAuthorizationGrantTypes()))
				.scopes(s -> s.addAll(existing.getScopes()))
				.redirectUri(redirectUri != null ? redirectUri : "")
				.postLogoutRedirectUri(postLogoutUri != null ? postLogoutUri : "")
				.clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
				.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(30)).build())
				.build();
		registeredClientRepository.save(updated);
		log.info("[clients] updated: clientId={}", clientId);
		return ResponseEntity.ok("Client updated successfully: " + clientId);
	}

	@DeleteMapping("/{clientId}")
	public ResponseEntity<String> deleteClient(@PathVariable String clientId) {
		log.info("[clients] delete clientId={}", clientId);
		RegisteredClient client = registeredClientRepository.findByClientId(clientId);
		if (client == null) {
			log.warn("[clients] delete not found: clientId={}", clientId);
			return ResponseEntity.notFound().build();
		}
		String id = client.getId();
		jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", id);
		jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", id);
		jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
		log.info("[clients] deleted: clientId={}", clientId);
		return ResponseEntity.ok("Client deleted: " + clientId);
	}
}
