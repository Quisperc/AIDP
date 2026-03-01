package cn.civer.authserver.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SSO 全局退出：清除该用户的授权同意并向所有客户端发送 OIDC Back-Channel Logout。
 * 可供「用户主动登出」与「修改账号/密码成功后」等场景复用。
 */
@Service
public class SsoLogoutService {

	private final JdbcTemplate jdbcTemplate;
	private final JWKSource<SecurityContext> jwkSource;
	private final AuthorizationServerSettings authorizationServerSettings;

	public SsoLogoutService(JdbcTemplate jdbcTemplate,
			JWKSource<SecurityContext> jwkSource,
			AuthorizationServerSettings authorizationServerSettings) {
		this.jdbcTemplate = jdbcTemplate;
		this.jwkSource = jwkSource;
		this.authorizationServerSettings = authorizationServerSettings;
	}

	/**
	 * 执行全局退出：清除授权同意并向后端通知所有客户端退出该用户会话。
	 *
	 * @param principalName 当前主体名（一般为用户名，与 oauth2_authorization_consent.principal_name 一致）
	 * @param issuer        签发者 URL，若为 null 则使用 AuthorizationServerSettings 中的配置
	 */
	public void performGlobalLogout(String principalName, String issuer) {
		if (principalName == null || principalName.isBlank()) {
			return;
		}
		String sqlDeleteConsent = "DELETE FROM oauth2_authorization_consent WHERE principal_name = ?";
		jdbcTemplate.update(sqlDeleteConsent, principalName);
		System.out.println("SSO Logout: Cleared all consents for user '" + principalName + "'");

		String resolvedIssuer = (issuer != null && !issuer.isBlank())
				? issuer
				: authorizationServerSettings.getIssuer();
		if (resolvedIssuer == null) {
			System.err.println("SSO Logout: No issuer configured, skip broadcast");
			return;
		}
		broadcastBackchannelLogout(principalName, resolvedIssuer);
	}

	/**
	 * 仅根据配置的 issuer 执行全局退出（适用于无 HttpServletRequest 的调用，如修改用户信息成功后）。
	 */
	public void performGlobalLogout(String principalName) {
		performGlobalLogout(principalName, null);
	}

	/**
	 * 校验 redirect_uri 是否允许（仅允许已注册客户端 redirect_uris 所在 host 的 URL），用于 logout 后跳回指定客户端。
	 */
	public boolean isAllowedLogoutRedirect(String redirectUri) {
		try {
			java.net.URI uri = new java.net.URI(redirectUri.trim());
			String scheme = uri.getScheme();
			if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)))
				return false;
			String host = uri.getHost();
			if (host == null || host.isBlank()) return false;
			String sql = "SELECT redirect_uris FROM oauth2_registered_client";
			java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			java.util.Set<String> allowedHosts = new java.util.HashSet<>();
			for (java.util.Map<String, Object> row : rows) {
				Object val = row.get("redirect_uris");
				String blobs = val != null ? val.toString() : null;
				if (blobs == null) continue;
				for (String one : blobs.split(",")) {
						if (one == null || one.isBlank()) continue;
						try {
							java.net.URI u = new java.net.URI(one.trim());
							if (u.getHost() != null) allowedHosts.add(u.getHost().toLowerCase());
						} catch (Exception ignored) { }
					}
			}
			return allowedHosts.contains(host.toLowerCase());
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 解析 issuer：优先使用配置，否则从 request 推导（供 Handler 等有请求上下文的场景使用）。
	 */
	public String getResolvedIssuer(HttpServletRequest request) {
		String issuer = authorizationServerSettings.getIssuer();
		if (issuer != null && !issuer.isBlank()) {
			return issuer;
		}
		if (request != null) {
			issuer = request.getScheme() + "://" + request.getServerName();
			if (("http".equals(request.getScheme()) && request.getServerPort() != 80) ||
					("https".equals(request.getScheme()) && request.getServerPort() != 443)) {
				issuer += ":" + request.getServerPort();
			}
			return issuer;
		}
		return null;
	}

	private void broadcastBackchannelLogout(String principalName, String issuer) {
		try {
			org.springframework.security.oauth2.jwt.NimbusJwtEncoder jwtEncoder =
					new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(jwkSource);

			String sqlSelectClients = "SELECT client_id, redirect_uris FROM oauth2_registered_client";
			java.util.List<java.util.Map<String, Object>> clientRows = jdbcTemplate.queryForList(sqlSelectClients);

			java.util.Set<String> processedClientUrls = new java.util.HashSet<>();

			for (java.util.Map<String, Object> row : clientRows) {
				String clientId = (String) row.get("client_id");
				String blobs = (String) row.get("redirect_uris");
				if (blobs == null) continue;

				String[] uris = blobs.split(",");
				java.util.Set<String> thisClientUrls = new java.util.HashSet<>();
				for (String uri : uris) {
					if (uri.isBlank()) continue;
					try {
						java.net.URI u = new java.net.URI(uri.trim());
						String logoutUrl = u.getScheme() + "://" + u.getHost()
								+ (u.getPort() != -1 ? ":" + u.getPort() : "") + "/api/sso-logout";
						thisClientUrls.add(logoutUrl);
					} catch (Exception ignored) { }
				}

				for (String logoutUrl : thisClientUrls) {
					String key = clientId + "|" + logoutUrl;
					if (processedClientUrls.contains(key)) continue;
					processedClientUrls.add(key);
					try {
						String logoutToken = generateLogoutToken(jwtEncoder, principalName, clientId, issuer);
						sendLogoutToken(logoutUrl, logoutToken);
					} catch (Exception e) {
						System.err.println("DEBUG: Error broadcasting logout to " + logoutUrl + ": " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error during OIDC broadcast logout: " + e.getMessage());
		}
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
				.claim("sid", principalName)
				.build();
		return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(jwsHeader, claims))
				.getTokenValue();
	}

	private void sendLogoutToken(String url, String token) {
		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString("logout_token=" + token))
				.build();
		client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding())
				.thenAccept(res -> System.out.println("Logout token sent to " + url + " code=" + res.statusCode()))
				.exceptionally(ex -> {
					System.err.println("Failed to send token to " + url + ": " + ex.getMessage());
					return null;
				});
	}
}
