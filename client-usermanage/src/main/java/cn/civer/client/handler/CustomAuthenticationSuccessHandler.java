package cn.civer.client.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {

		var savedRequest = new org.springframework.security.web.savedrequest.HttpSessionRequestCache()
				.getRequest(request, response);

		// Default to root if no saved request, or get the saved redirect URL
		String targetUrl = (savedRequest != null) ? savedRequest.getRedirectUrl() : "/";

		// Strip 'continue' parameter if present
		if (targetUrl.contains("continue")) {
			targetUrl = org.springframework.web.util.UriComponentsBuilder.fromUriString(targetUrl)
					.replaceQueryParam("continue")
					.build()
					.toUriString();
		}

		// Ensure we don't end up with an empty URL or just '?'
		if (targetUrl == null || targetUrl.isEmpty()) {
			targetUrl = "/";
		}

		// Debug log to confirm handler is running
		System.out.println("CustomAuthenticationSuccessHandler: Redirecting to " + targetUrl);

		getRedirectStrategy().sendRedirect(request, response, targetUrl);
	}
}
