package cn.civer.authserver.service;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 签发并推送 OIDC Back-Channel Logout 的 logout_token。
 *
 * 单独成类，是因为它的失败模式和「谁该被登出」完全不同：
 * 这里是网络与签名（超时、TLS、对端 5xx），那边是业务规则（该清谁的 consent、允许跳回哪）。
 * 混在一个类里时，排障要先判断是逻辑没触发还是推送没到达。
 *
 * 包内可见：只由 {@link SsoLogoutService} 使用，不对外暴露。
 */
class BackchannelLogoutSender {

	private static final Logger log = LoggerFactory.getLogger(BackchannelLogoutSender.class);

	private static final String BACKCHANNEL_EVENT = "http://schemas.openid.net/event/backchannel-logout";

	private final JwtEncoder jwtEncoder;
	private final String clientLogoutPath;
	private final HttpClient httpClient;
	private final ExecutorService executor;

	BackchannelLogoutSender(JWKSource<SecurityContext> jwkSource, String clientLogoutPath) {
		this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
		this.clientLogoutPath = clientLogoutPath.startsWith("/") ? clientLogoutPath : "/" + clientLogoutPath;
		// 复用连接与线程池：原先每次登出都新建客户端、且没有超时，对端不响应时会一直挂着
		this.executor = Executors.newFixedThreadPool(4, runnable -> {
			Thread t = new Thread(runnable, "sso-logout-broadcast");
			t.setDaemon(true);
			return t;
		});
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.executor(executor)
				.build();
	}

	/**
	 * 生成 logout_token。
	 *
	 * @param principalName 主体名，写进 sub
	 * @param audience      客户端 client_id
	 * @param issuer        认证中心 issuer，对端据它拉 JWKS 验签
	 */
	String generate(String principalName, String audience, String issuer) {
		JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(principalName)
				.audience(Collections.singletonList(audience))
				.issuedAt(Instant.now())
				.id(UUID.randomUUID().toString())
				.claim("events", Collections.singletonMap(BACKCHANNEL_EVENT, new HashMap<>()))
				// 规范要求 sid 是会话 ID；本项目会话与主体一一对应，直接复用主体名，
				// 对端按 sub 失效本地会话即可。
				.claim("sid", principalName)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
	}

	/** 异步推送；调用方不需要等待结果，失败只记日志（登出不能因为某个子系统挂了而卡住） */
	void send(String origin, String token) {
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(origin + clientLogoutPath))
				.timeout(Duration.ofSeconds(5))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("logout_token=" + token))
				.build();
		httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
				.thenAccept(res -> log.debug("[sso-logout] token delivered: origin={}, status={}",
						origin, res.statusCode()))
				.exceptionally(ex -> {
					log.warn("[sso-logout] token delivery failed: origin={}, reason={}",
							origin, ex.getMessage());
					return null;
				});
	}

	void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
