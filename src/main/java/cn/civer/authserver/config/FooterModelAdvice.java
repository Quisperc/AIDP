package cn.civer.authserver.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 把备案信息作为 {@code footer} 一个模型属性提供给所有视图。
 *
 * 原先是 8 个 @ModelAttribute 方法（其中 footerIcpIcon / footerPoliceIcon 从未被模板引用），
 * 相当于把「一个页脚」拆成 6 个全局变量：模板里写错名字只会渲染成空白，没有任何提示。
 * 现在模板用 {@code footer.icp} 取值，结构由 {@link FooterView} 固定。
 */
@ControllerAdvice
@EnableConfigurationProperties(FooterProperties.class)
public class FooterModelAdvice {

	private final FooterProperties properties;

	public FooterModelAdvice(FooterProperties properties) {
		this.properties = properties;
	}

	@ModelAttribute("footer")
	public FooterView footer(HttpServletRequest request) {
		String contextPath = request.getContextPath();
		return new FooterView(
				properties.icp(),
				properties.icpUrl(),
				resolveIconSrc(properties.icpIcon(), contextPath),
				properties.police(),
				properties.policeUrl(),
				resolveIconSrc(properties.policeIcon(), contextPath));
	}

	/** 解析图标地址：绝对 URL 原样返回，相对路径前加 contextPath */
	private static String resolveIconSrc(String path, String contextPath) {
		String normalized = normalizeStaticPath(path);
		if (normalized.isEmpty()) {
			return "";
		}
		if (normalized.startsWith("http") || normalized.startsWith("//")) {
			return normalized;
		}
		return (contextPath != null ? contextPath : "") + normalized;
	}

	/** 静态资源路径规范化：static 目录对应根路径，/static 前缀需去掉；相对路径补 / */
	private static String normalizeStaticPath(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		String s = path.trim();
		if (s.startsWith("/static")) {
			s = s.substring(7).isEmpty() ? "/" : s.substring(7);
		} else if (!s.startsWith("/") && !s.startsWith("http")) {
			s = "/" + s;
		}
		return s;
	}
}
