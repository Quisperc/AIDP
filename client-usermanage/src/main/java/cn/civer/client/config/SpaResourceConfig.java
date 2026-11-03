package cn.civer.client.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA 路由回退。
 *
 * 前端用的是 history 模式路由（/users、/clients、/profile）。
 * 直接刷新这些地址时，后端找不到对应静态文件会返回 404 —— 用户手动刷新就白屏。
 * 这里把「静态目录下找不到」的请求都回退到 index.html，交由前端路由接管。
 *
 * 接口路径不受影响：/admin/**、/api/** 会先被 SecurityFilterChain 处理，
 * 走不到资源解析器。
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/static/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable() && !isDirectory(requested)) {
							return requested;
						}
						return new ClassPathResource("/static/index.html");
					}
				});
	}

	private static boolean isDirectory(Resource resource) {
		try {
			return resource.getFile().isDirectory();
		} catch (IOException e) {
			// jar 包内无法取 File，按非目录处理即可
			return false;
		}
	}
}
