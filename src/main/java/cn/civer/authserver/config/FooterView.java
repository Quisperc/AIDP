package cn.civer.authserver.config;

import lombok.Getter;

/**
 * 页脚视图对象：模板只认这一组字段，改名会在编译期/模板渲染时立刻暴露，
 * 而不是像原来那样渲染成空白。
 *
 * 用普通类 + getter 而不是 record：Thymeleaf 的 SpEL 属性访问走 getXxx()，
 * record 的 xxx() 访问器不在它的默认识别范围内。
 */
@Getter
public class FooterView {

	/** ICP 备案号，空串表示不展示 */
	private final String icp;
	/** ICP 备案查询地址 */
	private final String icpUrl;
	/** 已按 contextPath 解析过的 ICP 图标地址 */
	private final String icpIconSrc;
	/** 公安备案号，空串表示不展示 */
	private final String police;
	/** 公安备案查询地址 */
	private final String policeUrl;
	/** 已按 contextPath 解析过的公安备案图标地址 */
	private final String policeIconSrc;

	public FooterView(String icp, String icpUrl, String icpIconSrc,
			String police, String policeUrl, String policeIconSrc) {
		this.icp = icp;
		this.icpUrl = icpUrl;
		this.icpIconSrc = icpIconSrc;
		this.police = police;
		this.policeUrl = policeUrl;
		this.policeIconSrc = policeIconSrc;
	}
}
