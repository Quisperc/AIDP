package cn.civer.authserver.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class FooterModelAdvice {

	@Value("${app.footer.icp:}")
	private String footerIcp;

	@Value("${app.footer.icp-url:https://beian.miit.gov.cn/}")
	private String footerIcpUrl;

	@Value("${app.footer.police:}")
	private String footerPolice;

	@Value("${app.footer.police-url:}")
	private String footerPoliceUrl;

	@Value("${app.footer.icp-icon:}")
	private String footerIcpIcon;

	@Value("${app.footer.police-icon:}")
	private String footerPoliceIcon;

	@ModelAttribute("footerIcp")
	public String footerIcp() {
		return footerIcp;
	}

	@ModelAttribute("footerIcpUrl")
	public String footerIcpUrl() {
		return footerIcpUrl;
	}

	@ModelAttribute("footerPolice")
	public String footerPolice() {
		return footerPolice;
	}

	@ModelAttribute("footerPoliceUrl")
	public String footerPoliceUrl() {
		return footerPoliceUrl;
	}

	@ModelAttribute("footerIcpIcon")
	public String footerIcpIcon() {
		return normalizeStaticPath(footerIcpIcon);
	}

	@ModelAttribute("footerPoliceIcon")
	public String footerPoliceIcon() {
		return normalizeStaticPath(footerPoliceIcon);
	}

	@ModelAttribute("footerIcpIconSrc")
	public String footerIcpIconSrc(HttpServletRequest request) {
		return resolveIconSrc(normalizeStaticPath(footerIcpIcon), request);
	}

	@ModelAttribute("footerPoliceIconSrc")
	public String footerPoliceIconSrc(HttpServletRequest request) {
		return resolveIconSrc(normalizeStaticPath(footerPoliceIcon), request);
	}

	/** 解析图标地址：绝对 URL 原样返回，相对路径前加 contextPath */
	private static String resolveIconSrc(String path, HttpServletRequest request) {
		if (path == null || path.isEmpty()) return "";
		if (path.startsWith("http") || path.startsWith("//")) return path;
		String ctx = request.getContextPath();
		return (ctx != null ? ctx : "") + path;
	}

	/** 静态资源路径规范化：Spring Boot 下 static 目录对应根路径，/static 前缀需去掉；相对路径补 / */
	private static String normalizeStaticPath(String path) {
		if (path == null || path.isEmpty()) return path;
		String s = path.trim();
		if (s.startsWith("/static")) s = s.substring(7).isEmpty() ? "/" : s.substring(7);
		else if (!s.startsWith("/") && !s.startsWith("http")) s = "/" + s;
		return s;
	}
}
