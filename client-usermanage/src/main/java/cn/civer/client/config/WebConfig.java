package cn.civer.client.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Explicit redirects for clean URLs
		registry.addRedirectViewController("/admin", "/admin/users");
		registry.addRedirectViewController("/admin/", "/admin/users");
		registry.addRedirectViewController("/user", "/user/profile");
		registry.addRedirectViewController("/user/", "/user/profile");
	}
}
