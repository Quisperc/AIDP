package cn.civer.authserver.service;

import cn.civer.authserver.exception.BusinessException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 邮箱的归一化与格式校验：账号自助（AccountService）与管理台（UserAdminService）
 * 两条写入路径共用同一套规则，避免一边归一化、另一边不归一化造成数据分叉。
 *
 * 为什么用 Jakarta Bean Validation 的 {@code @Email} 而不是自写正则：
 * OIDC Core 1.0 §5.1 要求 email claim 的值符合 RFC 5322 的 addr-spec 语法。
 * 原先的 {@code ^[^@\s]+@[^@\s]+\.[^@\s]+$} 只是「看起来像邮箱」的近似：
 * 它强制要求域名带点（会拒绝合法的 addr-spec），又接受一些不合法的形式。
 * 用标准约束实现，行为由 Hibernate Validator 维护，不再由业务代码自己兜。
 *
 * 这里刻意用 Jakarta Validation 的标准 API 建静态工厂，而不注入 Spring 的
 * LocalValidatorFactoryBean：本类不依赖 Spring 上下文，可独立做单元测试，
 * 也不必改动两个 Service 的构造函数（现有集成测试全部走 Spring 自动装配）。
 * 应用内只有这一处用到 Bean Validation，多一个工厂没有实际开销。
 */
public final class EmailPolicy {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	/** 承载 @Email 约束的最小载体；避免直接引用 Hibernate Validator 的 internal 包。 */
	private record EmailValue(@Email String value) {}

	private EmailPolicy() {
	}

	/**
	 * 归一化：去首尾空白 + 转小写；空白输入返回 null（调用方据此清空邮箱）。
	 *
	 * 为什么转小写：RFC 5322 理论上允许 local-part 区分大小写，但现实中的邮件
	 * 服务商普遍不区分；而 V3 建的 idx_users_email 是大小写敏感的唯一索引，
	 * 不归一化就会让 A@x.com 与 a@x.com 登记到两个账号上。用 Locale.ROOT
	 * 避免土耳其语等区域大小写规则把 I 转成带点字符。
	 */
	public static String normalize(String raw) {
		return StringUtils.hasText(raw) ? raw.trim().toLowerCase(Locale.ROOT) : null;
	}

	/**
	 * 按 RFC 5322 addr-spec 校验格式；失败抛 BusinessException，
	 * 由 Controller 的 PRG 流程捕获后塞进 flash 提示，而不是打到错误页。
	 * 传入的应是 {@link #normalize(String)} 的结果（非 null）。
	 */
	public static void requireValid(String normalized) {
		if (!VALIDATOR.validate(new EmailValue(normalized)).isEmpty()) {
			throw BusinessException.badRequest("邮箱格式不正确。");
		}
	}
}
