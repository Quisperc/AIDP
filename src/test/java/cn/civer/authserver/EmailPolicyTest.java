package cn.civer.authserver;

import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.service.EmailPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 邮箱策略的纯单元测试：不依赖 Spring 上下文，跑得快，
 * 用来锁死「归一化」与「标准格式校验」两件事的行为边界。
 */
class EmailPolicyTest {

	@Test
	@DisplayName("归一化：去首尾空白并转小写")
	void normalizeTrimsAndLowercases() {
		assertThat(EmailPolicy.normalize("  Alice.Zhang+Tag@Example.COM  "))
				.isEqualTo("alice.zhang+tag@example.com");
		assertThat(EmailPolicy.normalize("ME@EXAMPLE.COM")).isEqualTo("me@example.com");
	}

	@Test
	@DisplayName("归一化：空白输入返回 null，调用方据此清空邮箱")
	void normalizeBlankIsNull() {
		assertThat(EmailPolicy.normalize(null)).isNull();
		assertThat(EmailPolicy.normalize("   ")).isNull();
	}

	@Test
	@DisplayName("标准 @Email 接受常见合法 addr-spec")
	void acceptsStandardAddresses() {
		EmailPolicy.requireValid("alice@example.com");
		EmailPolicy.requireValid("a.b+tag@sub.example.co");
		// RFC 5322 允许域名不带点，旧正则会误拒；标准约束应当放行
		EmailPolicy.requireValid("user@localhost");
	}

	@Test
	@DisplayName("标准 @Email 拒绝非法格式，且抛 BusinessException 供 PRG 流程捕获")
	void rejectsMalformedAddresses() {
		for (String bad : new String[] {
				"not-an-email", "a@@b.com", "a b@example.com", "@example.com", "a@" }) {
			assertThatThrownBy(() -> EmailPolicy.requireValid(bad))
					.as(bad)
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("邮箱格式");
		}
	}
}
