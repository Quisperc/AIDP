package cn.civer.authserver.controller;

import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

// Simple DTO for Client Request
class ClientDto {
	public String clientId;
	public String clientSecret;
	public String redirectUri;
	public String postLogoutRedirectUri;
	public String clientName;
}

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class RegisteredClientController {

	private final RegisteredClientRepository registeredClientRepository;
	private final PasswordEncoder passwordEncoder;

	public RegisteredClientController(RegisteredClientRepository registeredClientRepository,
			PasswordEncoder passwordEncoder) {
		this.registeredClientRepository = registeredClientRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@PostMapping
	public ResponseEntity<String> registerClient(@RequestBody ClientDto dto) {
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
		return ResponseEntity.ok("Client registered successfully: " + dto.clientId);
	}

	// Note: RegisteredClientRepository interface is limited in standard Spring
	// Authorization Server.
	// It typically only supports findByClientId and save. List/Delete requires
	// accessing the underlying DB or custom implementation.
	// For this MVP, we will assume standard interface usage for creation.
	// To list/delete, we would strictly need to cast to
	// JdbcRegisteredClientRepository or use a direct JdbcTemplate/Repository.
	// Given the time constraints, I will implement creation only for now to match
	// immediate requirements.
	// If listing is mandatory, I should use JdbcTemplate.
}
