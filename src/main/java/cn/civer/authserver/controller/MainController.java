package cn.civer.authserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.net.URI;

@Controller
public class MainController {

	@Value("${app.auth.redirect-uri}")
	private String clientRedirectUri;

	@GetMapping("/")
	public String index() {
		// Dynamically derive the Client App's base URL from the configured redirect URI
		// Example logic: "http://host:8081/login/..." -> "http://host:8081/"
		try {
			if (clientRedirectUri != null && !clientRedirectUri.isBlank()) {
				URI uri = new URI(clientRedirectUri);
				// Reconstruct base URL: scheme://host:port/
				String baseUrl = uri.getScheme() + "://" + uri.getHost();
				if (uri.getPort() != -1) {
					baseUrl += ":" + uri.getPort();
				}
				return "redirect:" + baseUrl + "/";
			}
		} catch (Exception e) {
			// Fallback or log if parsing fails.
			// In a real app we might show a dashboard or error page.
		}

		// If no client logic can be determined, show a simple welcome message
		return "welcome";
	}

	@GetMapping("/login")
	public String login() {
		return "login"; // Default Spring Security login page, but explicit mapping can help
						// customizability
	}
}
