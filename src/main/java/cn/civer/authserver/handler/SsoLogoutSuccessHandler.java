package cn.civer.authserver.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SsoLogoutSuccessHandler implements LogoutSuccessHandler {

	private final JdbcTemplate jdbcTemplate;
	private final com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource;
	private final org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings authorizationServerSettings;

	public SsoLogoutSuccessHandler(JdbcTemplate jdbcTemplate,
			com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource,
			org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings authorizationServerSettings) {
		this.jdbcTemplate = jdbcTemplate;
		this.jwkSource = jwkSource;
		this.authorizationServerSettings = authorizationServerSettings;
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		if (authentication != null && authentication.getName() != null) {
			String principalName = authentication.getName();

			// 1. Clear consents
			String sqlDeleteConsent = "DELETE FROM oauth2_authorization_consent WHERE principal_name = ?";
			jdbcTemplate.update(sqlDeleteConsent, principalName);
			System.out.println("SSO Logout: Cleared all consents for user '" + principalName + "'");

			// Resolve Issuer (fallback to request URL if not configured)
			String issuer = authorizationServerSettings.getIssuer();
			if (issuer == null) {
				issuer = request.getScheme() + "://" + request.getServerName();
				if (("http".equals(request.getScheme()) && request.getServerPort() != 80) ||
						("https".equals(request.getScheme()) && request.getServerPort() != 443)) {
					issuer += ":" + request.getServerPort();
				}
			}
			System.out.println("DEBUG: Resolved Issuer: " + issuer);

			// 2. Broadcast OIDC Back-Channel Logout
			try {
				// Initialize JWT Encoder
				org.springframework.security.oauth2.jwt.NimbusJwtEncoder jwtEncoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
						jwkSource);

				// Find clients with redirect URIs
				String sqlSelectClients = "SELECT client_id, redirect_uris FROM oauth2_registered_client";
				java.util.List<java.util.Map<String, Object>> clientRows = jdbcTemplate.queryForList(sqlSelectClients);

				System.out.println("DEBUG: Found " + clientRows.size() + " clients in database.");

				// Deduplicate and process clients
				java.util.Set<String> processedClientUrls = new java.util.HashSet<>();

				for (java.util.Map<String, Object> row : clientRows) {
					String clientId = (String) row.get("client_id");
					String blobs = (String) row.get("redirect_uris");
					System.out.println("DEBUG: Checking client " + clientId + ", redirect_uris=" + blobs);

					if (blobs == null)
						continue;

					String[] uris = blobs.split(",");
					java.util.Set<String> thisClientUrls = new java.util.HashSet<>();

					// Collect valid logout URLs for this client
					for (String uri : uris) {
						if (uri.isBlank())
							continue;
						try {
							java.net.URI u = new java.net.URI(uri.trim());
							String logoutUrl = u.getScheme() + "://" + u.getHost()
									+ (u.getPort() != -1 ? ":" + u.getPort() : "") + "/api/sso-logout";
							thisClientUrls.add(logoutUrl);
						} catch (Exception e) {
							// ignore invalid URIs
						}
					}

					// Send Tokens
					for (String logoutUrl : thisClientUrls) {
						// Unique key per client+url to avoid duplicates if any
						String key = clientId + "|" + logoutUrl;
						if (processedClientUrls.contains(key))
							continue;
						processedClientUrls.add(key);

						try {
							System.out.println("DEBUG: Generating token for client " + clientId + " at " + logoutUrl);
							String logoutToken = generateLogoutToken(jwtEncoder, principalName, clientId, issuer);
							sendLogoutToken(logoutUrl, logoutToken);
						} catch (Exception e) {
							System.err.println("DEBUG: Error processing logout for " + clientId + " to " + logoutUrl
									+ ": " + e.getMessage());
							e.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				System.err.println("Error during OIDC broadcast logout: " + e.getMessage());
				e.printStackTrace();
			}
		}

		response.sendRedirect("/login?logout");
	}

	private String generateLogoutToken(org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder,
			String principalName, String audience, String issuer) {
		org.springframework.security.oauth2.jwt.JwsHeader jwsHeader = org.springframework.security.oauth2.jwt.JwsHeader
				.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();

		org.springframework.security.oauth2.jwt.JwtClaimsSet claims = org.springframework.security.oauth2.jwt.JwtClaimsSet
				.builder()
				.issuer(issuer)
				.subject(principalName)
				.audience(java.util.Collections.singletonList(audience))
				.issuedAt(java.time.Instant.now())
				.id(java.util.UUID.randomUUID().toString())
				.claim("events",
						java.util.Collections.singletonMap("http://schemas.openid.net/event/backchannel-logout",
								new java.util.HashMap<>()))
				.claim("sid", principalName) // Using username as session ID for simple matching
				.build();

		return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(jwsHeader, claims))
				.getTokenValue();
	}

	private void sendLogoutToken(String url, String token) {
		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		System.out.println("OIDC Back-Channel Logout to: " + url);

		java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString("logout_token=" + token))
				.build();

		client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding())
				.thenAccept(res -> System.out
						.println("Logout token sent to " + url + " code=" + res.statusCode()))
				.exceptionally(ex -> {
					System.err.println("Failed to send token to " + url + ": " + ex.getMessage());
					return null;
				});
	}
}
