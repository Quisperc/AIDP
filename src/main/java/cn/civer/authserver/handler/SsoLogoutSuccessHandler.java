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

	@org.springframework.beans.factory.annotation.Value("${app.auth.sso-secret}")
	private String ssoSecret;

	public SsoLogoutSuccessHandler(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
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

			// 2. Broadcast Logout to all Clients
			// Since RegisteredClientRepository usually doesn't strictly support "findAll",
			// we use JdbcTemplate to find all redirect_uris to guess the client hosts.
			// Note: This is a simplified implementation. In prod, clients should register a
			// specific "backchannel_logout_uri".
			try {
				String sqlSelectUris = "SELECT redirect_uris FROM oauth2_registered_client";
				java.util.List<String> uriBlobs = jdbcTemplate.queryForList(sqlSelectUris, String.class);

				java.util.Set<String> notifyUrls = new java.util.HashSet<>();
				for (String blobs : uriBlobs) {
					// redirects are comma separated in DB usually if flattened, or just one per
					// row?
					// Spring Security JDBC impl usually stores them as comma-delimited string in
					// 'redirect_uris' column.
					if (blobs == null)
						continue;
					String[] uris = blobs.split(",");
					for (String uri : uris) {
						if (uri.isBlank())
							continue;
						try {
							java.net.URI u = new java.net.URI(uri.trim());
							// Construct logout URL: scheme://host:port/api/sso-logout
							String logoutUrl = u.getScheme() + "://" + u.getHost()
									+ (u.getPort() != -1 ? ":" + u.getPort() : "") + "/api/sso-logout";
							notifyUrls.add(logoutUrl);
						} catch (Exception e) {
							// ignore invalid URIs
						}
					}
				}

				// Send async notifications
				java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
				for (String notifyUrl : notifyUrls) {
					// ... inside broadcast loop
					System.out.println("Broadcasting logout to: " + notifyUrl);
					java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
							.uri(java.net.URI.create(notifyUrl))
							.header("Content-Type", "application/x-www-form-urlencoded")
							.header("X-SSO-Secret", ssoSecret)
							.POST(java.net.http.HttpRequest.BodyPublishers.ofString("username=" + principalName))
							.build();

					// Fire and forget (async)
					client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding())
							.thenAccept(res -> System.out
									.println("Logout sent to " + notifyUrl + " code=" + res.statusCode()))
							.exceptionally(ex -> {
								System.err.println("Failed to send logout to " + notifyUrl + ": " + ex.getMessage());
								return null;
							});
				}
			} catch (Exception e) {
				System.err.println("Error during broadcast logout: " + e.getMessage());
				e.printStackTrace();
			}
		}

		response.sendRedirect("/login?logout");
	}
}
