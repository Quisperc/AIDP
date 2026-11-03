package cn.civer.client.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

	private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {

		var savedRequest = requestCache.getRequest(request, response);

		// Default to root if no saved request, or get the saved redirect URL
		String targetUrl = (savedRequest != null) ? savedRequest.getRedirectUrl() : "/";

		// Strip 'continue' parameter if present
		if (targetUrl.contains("continue")) {
			targetUrl = UriComponentsBuilder.fromUriString(targetUrl)
					.replaceQueryParam("continue")
					.build()
					.toUriString();
		}

		// Ensure we don't end up with an empty URL or just '?'
		if (targetUrl == null || targetUrl.isEmpty()) {
			targetUrl = "/";
		}

		log.debug("[login-success] redirecting to {}", targetUrl);

		getRedirectStrategy().sendRedirect(request, response, targetUrl);
	}
}
