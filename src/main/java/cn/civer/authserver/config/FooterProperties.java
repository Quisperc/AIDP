package cn.civer.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 底部备案信息（ICP / 公安备案）。
 *
 * 原先靠 6 个 @Value 分散在 FooterModelAdvice 里，缺一个就静默变成 null，
 * 且没有任何地方能一眼看出「这个应用有哪些备案配置项」。改为类型化绑定后，
 * 缺键、拼错键会在启动时就暴露。
 */
@ConfigurationProperties(prefix = "app.footer")
public record FooterProperties(
		@DefaultValue("") String icp,
		@DefaultValue("https://beian.miit.gov.cn/") String icpUrl,
		@DefaultValue("") String icpIcon,
		@DefaultValue("") String police,
		@DefaultValue("") String policeUrl,
		@DefaultValue("") String policeIcon) {
}
