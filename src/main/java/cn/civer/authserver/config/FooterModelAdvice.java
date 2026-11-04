package cn.civer.authserver.config;

import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 把备案信息作为 {@code footer} 一个模型属性提供给所有视图。
 *
 * 原先是 8 个 @ModelAttribute 方法（其中 footerIcpIcon / footerPoliceIcon 从未被模板引用），
 * 相当于把「一个页脚」拆成 6 个全局变量：模板里写错名字只会渲染成空白，没有任何提示。
 * 现在模板用 {@code footer.icp} 取值，结构由 {@link FooterView} 固定。
 *
 * 另提供 displayName：按登录名查库，昵称优先、回退登录名。安全主体保持框架内置类型
 * （自定义 UserDetails 会让 OAuth2Authorization 反序列化失败），展示名在这里单独查。
 * 匿名或 @WithMockUser 这类库里没有的主体，安全回退到 authentication.getName()。
 */
@ControllerAdvice
@EnableConfigurationProperties(FooterProperties.class)
public class FooterModelAdvice {

	private final FooterProperties properties;
	private final UserRepository userRepository;

	public FooterModelAdvice(FooterProperties properties, UserRepository userRepository) {
		this.properties = properties;
		this.userRepository = userRepository;
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

	@ModelAttribute("displayName")
	public String displayName() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !StringUtils.hasText(authentication.getName())) {
			return "";
		}
		return userRepository.findByUsername(authentication.getName())
				.map(FooterModelAdvice::displayNameOf)
				.orElse(authentication.getName());
	}

	private static String displayNameOf(User user) {
		return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
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
