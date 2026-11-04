package cn.civer.authserver;

import cn.civer.authserver.config.TokenConfig;
import cn.civer.authserver.entity.User;
import cn.civer.authserver.repository.UserRepository;
import cn.civer.authserver.security.SigningKeyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V3 之后 ID Token 的两条硬规则，必须各有一条回归守着：
 * 1. sub 只能用不可变的 public_id（可变的 username 会让客户端账号关联失配）；
 * 2. email / nickname 等标准 claim 只在 ID Token、且只在该次授权确实包含对应
 *    scope 时写入，否则只授权 openid/profile 的客户端也能从 id_token 读到邮箱。
 *
 * 纯单元测试：用框架公开的 JwtEncodingContext.Builder 构造真实上下文，
 * 只 mock UserRepository，不启动 Spring 容器。
 */
class TokenClaimsTest {

	private static final String LOGIN_NAME = "alice";
	private static final String PUBLIC_ID = "7c1f2b6e-4c9a-4a1e-9b0d-2f0e9a3c8b51";
	private static final String INITIAL_SUBJECT = "placeholder-subject";

	private UserRepository userRepository;
	private OAuth2TokenCustomizer<JwtEncodingContext> customizer;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		// jwtTokenCustomizer 不触碰 SigningKeyStore，构造器只保存引用
		customizer = new TokenConfig(mock(SigningKeyStore.class), userRepository).jwtTokenCustomizer();
	}

	@Test
	@DisplayName("ID Token 全 scope：sub 为 public_id，标准 claim 按 scope 写入")
	void idTokenWithAllScopesWritesStandardClaims() {
		givenAccount(true);
		JwtClaimsSet claims = encode(OidcParameterNames.ID_TOKEN,
				Set.of("openid", "profile", "email"));

		assertThat(claims.getSubject()).isEqualTo(PUBLIC_ID);
		assertThat(claims.getClaims().get("email")).isEqualTo("alice@example.com");
		assertThat(claims.getClaims().get("email_verified")).isEqualTo(false);
		assertThat(claims.getClaims().get("nickname")).isEqualTo("小陈");
		assertThat(claims.getClaims().get("preferred_username")).isEqualTo(LOGIN_NAME);
		assertThat(claims.getClaims().get("roles")).isEqualTo(Set.of("ROLE_USER"));
	}

	@Test
	@DisplayName("未授权 email 时不得把邮箱写进 ID Token")
	void idTokenWithoutEmailScopeDoesNotLeakEmail() {
		givenAccount(true);
		JwtClaimsSet claims = encode(OidcParameterNames.ID_TOKEN, Set.of("openid", "profile"));

		assertThat(claims.getSubject()).isEqualTo(PUBLIC_ID);
		assertThat(claims.getClaims().get("email")).isNull();
		assertThat(claims.getClaims().get("email_verified")).isNull();
		assertThat(claims.getClaims().get("nickname")).isEqualTo("小陈");
	}

	@Test
	@DisplayName("自助填写的邮箱必须标记未验证，昵称留空时回退登录名")
	void unverifiedEmailAndNicknameFallback() {
		givenAccount(false);
		JwtClaimsSet claims = encode(OidcParameterNames.ID_TOKEN,
				Set.of("openid", "profile", "email"));

		assertThat(claims.getClaims().get("email_verified")).isEqualTo(false);
		assertThat(claims.getClaims().get("nickname")).isEqualTo(LOGIN_NAME);
	}

	@Test
	@DisplayName("Access Token 不覆写 sub，也不带 profile / email 专属 claim")
	void accessTokenKeepsFrameworkSubjectAndHasNoProfileClaims() {
		givenAccount(true);
		JwtClaimsSet claims = encode(OAuth2TokenType.ACCESS_TOKEN.getValue(),
				Set.of("openid", "profile", "email"));

		assertThat(claims.getSubject()).isEqualTo(INITIAL_SUBJECT);
		assertThat(claims.getClaims().get("email")).isNull();
		assertThat(claims.getClaims().get("nickname")).isNull();
		assertThat(claims.getClaims().get("roles")).isEqualTo(Set.of("ROLE_USER"));
	}

	@Test
	@DisplayName("账号查不到时保持框架原值，不抛异常、不编造 claim")
	void missingAccountKeepsFrameworkValues() {
		when(userRepository.findByUsername(LOGIN_NAME)).thenReturn(Optional.empty());
		JwtClaimsSet claims = encode(OidcParameterNames.ID_TOKEN,
				Set.of("openid", "profile", "email"));

		assertThat(claims.getSubject()).isEqualTo(INITIAL_SUBJECT);
		assertThat(claims.getClaims().get("email")).isNull();
	}

	private void givenAccount(boolean withNickname) {
		User account = new User(LOGIN_NAME, "hash", "ROLE_USER");
		account.setPublicId(PUBLIC_ID);
		account.setEmail("alice@example.com");
		account.setEmailVerified(false);
		if (withNickname) {
			account.setNickname("小陈");
		}
		when(userRepository.findByUsername(LOGIN_NAME)).thenReturn(Optional.of(account));
	}

	private JwtClaimsSet encode(String tokenTypeValue, Set<String> scopes) {
		JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
				.issuer("https://idp.example.com")
				.subject(INITIAL_SUBJECT)
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60));
		Authentication principal = new UsernamePasswordAuthenticationToken(
				org.springframework.security.core.userdetails.User.withUsername(LOGIN_NAME)
						.password("irrelevant")
						.authorities("ROLE_USER")
						.build(),
				null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
		JwtEncodingContext context = JwtEncodingContext
				.with(JwsHeader.with(SignatureAlgorithm.RS256), claimsBuilder)
				.principal(principal)
				.tokenType(new OAuth2TokenType(tokenTypeValue))
				.authorizedScopes(scopes)
				.build();

		customizer.customize(context);
		return claimsBuilder.build();
	}
}
