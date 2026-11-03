package cn.civer.authserver.service;

import cn.civer.authserver.dto.ClientDto;
import cn.civer.authserver.dto.ClientSummaryDto;
import cn.civer.authserver.exception.BusinessException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端（RegisteredClient）业务逻辑。
 *
 * 原先这些逻辑全在 RegisteredClientController 里：拼接 RegisteredClient.Builder、
 * 手写多条 DELETE、直接查 oauth2_registered_client 表。Controller 因此既管协议又管业务，
 * 加任何规则都只能继续往里堆。这里把它们收拢，Controller 只负责校验入参与组装响应。
 */
@Service
public class ClientService {

	private static final Logger log = LoggerFactory.getLogger(ClientService.class);
	/**
	 * 列表投影。client_settings 是一段 JSON，里面存着 PKCE 等客户端级设置——
	 * 之前没取这一列，列表里 requirePkce 恒为 false，前端只能写「需查看详情」。
	 */
	private static final String LIST_SQL =
			"SELECT id, client_id, client_name, redirect_uris, post_logout_redirect_uris, client_settings "
					+ "FROM oauth2_registered_client ORDER BY client_id";
	/** client_settings 里 PKCE 开关的键名（Spring Authorization Server 的序列化约定） */
	private static final String REQUIRE_PROOF_KEY = "settings.client.require-proof-key";

	private final RegisteredClientRepository registeredClientRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;
	private final SsoLogoutService ssoLogoutService;
	private final ObjectMapper objectMapper;
	private final Duration accessTokenTtl;

	public ClientService(RegisteredClientRepository registeredClientRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate,
			SsoLogoutService ssoLogoutService,
			ObjectMapper objectMapper,
			@Value("${app.auth.access-token-time-to-live:30m}") Duration accessTokenTtl) {
		this.registeredClientRepository = registeredClientRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
		this.ssoLogoutService = ssoLogoutService;
		this.objectMapper = objectMapper;
		this.accessTokenTtl = accessTokenTtl;
	}

	public List<ClientSummaryDto> listClients() {
		return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> new ClientSummaryDto(
				rs.getString("id"),
				rs.getString("client_id"),
				rs.getString("client_name"),
				// 库里 redirect_uris 是逗号分隔（JdbcRegisteredClientRepository 的存法），
				// 详情接口返回的是换行分隔。同一个字段两种格式，前端按 \n 拆分时会漏掉
				// 多 URI 客户端的「+n」提示，这里统一成换行。
				toLineSeparated(rs.getString("redirect_uris")),
				rs.getString("post_logout_redirect_uris"),
				requireProofKey(rs.getString("client_settings"))));
	}

	/**
	 * 从 client_settings 的 JSON 里读 PKCE 开关。
	 *
	 * 早期实现用正则匹配 "require-proof-key":true —— 对着 JSON 做正则，格式一变就静默出错，
	 * 且拿不到「明确写了 false」与「压根没这个键」的区别。改为正常解析。
	 *
	 * 注意这里注入的是 Jackson 3（tools.jackson）的 ObjectMapper：
	 * Spring Boot 4 已把默认 JSON 实现迁到 Jackson 3，com.fasterxml 的 ObjectMapper
	 * 不在容器里，注入会直接启动失败。
	 */
	private boolean requireProofKey(String settingsJson) {
		if (!StringUtils.hasText(settingsJson)) {
			return false;
		}
		try {
			Map<String, Object> settings = objectMapper.readValue(settingsJson,
					new TypeReference<Map<String, Object>>() {
					});
			return Boolean.TRUE.equals(settings.get(REQUIRE_PROOF_KEY));
		} catch (Exception e) {
			log.warn("[clients] client_settings 解析失败，PKCE 按未开启处理: {}", e.getMessage());
			return false;
		}
	}

	private static String toLineSeparated(String raw) {
		if (!StringUtils.hasText(raw)) {
			return "";
		}
		return String.join("\n", Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList());
	}

	public ClientSummaryDto getClient(String clientId) {
		RegisteredClient client = registeredClientRepository.findByClientId(clientId);
		if (client == null) {
			throw BusinessException.notFound("客户端不存在: " + clientId);
		}
		return toSummary(client);
	}

	@Transactional
	public void register(ClientDto dto) {
		if (!StringUtils.hasText(dto.clientId()) || !StringUtils.hasText(dto.clientSecret())) {
			throw BusinessException.badRequest("Client ID 与 Client Secret 均不能为空。");
		}
		if (registeredClientRepository.findByClientId(dto.clientId()) != null) {
			throw BusinessException.conflict("Client ID 已被使用: " + dto.clientId());
		}
		List<String> redirectUris = parseRedirectUris(dto.redirectUri());
		if (redirectUris.isEmpty()) {
			throw BusinessException.badRequest("至少需要一个 redirect_uri。");
		}
		registeredClientRepository.save(buildClient(UUID.randomUUID().toString(), dto.clientId(),
				dto.clientSecret(), dto.clientName(), redirectUris, dto.postLogoutRedirectUri(),
				dto.requirePkce() != null ? dto.requirePkce() : true));
		onClientChanged();
		log.info("[clients] registered: clientId={}", dto.clientId());
	}

	@Transactional
	public void update(String clientId, ClientDto dto) {
		RegisteredClient existing = registeredClientRepository.findByClientId(clientId);
		if (existing == null) {
			throw BusinessException.notFound("客户端不存在: " + clientId);
		}
		List<String> redirectUris = parseRedirectUris(dto.redirectUri());
		if (redirectUris.isEmpty()) {
			if (existing.getRedirectUris() == null || existing.getRedirectUris().isEmpty()) {
				throw BusinessException.badRequest("至少需要一个 redirect_uri。");
			}
			redirectUris = existing.getRedirectUris().stream().toList();
		}
		boolean pkce = dto.requirePkce() != null ? dto.requirePkce() : existing.getClientSettings().isRequireProofKey();
		String secret = StringUtils.hasText(dto.clientSecret())
				? passwordEncoder.encode(dto.clientSecret())
				: existing.getClientSecret();
		String name = StringUtils.hasText(dto.clientName()) ? dto.clientName() : existing.getClientName();
		String postLogout = StringUtils.hasText(dto.postLogoutRedirectUri())
				? dto.postLogoutRedirectUri()
				: (existing.getPostLogoutRedirectUris().isEmpty()
						? null
						: existing.getPostLogoutRedirectUris().iterator().next());

		RegisteredClient.Builder builder = RegisteredClient.withId(existing.getId())
				.clientId(existing.getClientId())
				.clientSecret(secret)
				.clientName(name)
				.clientAuthenticationMethods(c -> c.addAll(existing.getClientAuthenticationMethods()))
				.authorizationGrantTypes(g -> g.addAll(existing.getAuthorizationGrantTypes()))
				.scopes(s -> s.addAll(existing.getScopes()));
		redirectUris.forEach(builder::redirectUri);
		registeredClientRepository.save(builder
				.postLogoutRedirectUri(postLogout != null ? postLogout : "")
				.clientSettings(clientSettings(pkce))
				.tokenSettings(tokenSettings())
				.build());
		onClientChanged();
		log.info("[clients] updated: clientId={}", clientId);
	}

	/**
	 * 授权、同意、客户端三张表必须一起清掉，否则会留下孤儿授权记录。
	 */
	@Transactional
	public void delete(String clientId) {
		RegisteredClient client = registeredClientRepository.findByClientId(clientId);
		if (client == null) {
			throw BusinessException.notFound("客户端不存在: " + clientId);
		}
		String id = client.getId();
		jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", id);
		jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", id);
		jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
		onClientChanged();
		log.info("[clients] deleted: clientId={}", clientId);
	}

	/**
	 * redirect_uris 变了，登出跳转的 host 白名单缓存需立即失效。
	 */
	private void onClientChanged() {
		ssoLogoutService.invalidateHostCache();
	}

	private RegisteredClient buildClient(String id, String clientId, String clientSecret, String clientName,
			List<String> redirectUris, String postLogoutRedirectUri, boolean requirePkce) {
		RegisteredClient.Builder builder = RegisteredClient.withId(id)
				.clientId(clientId)
				.clientSecret(passwordEncoder.encode(clientSecret))
				.clientName(clientName)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
		redirectUris.forEach(builder::redirectUri);
		return builder
				.postLogoutRedirectUri(postLogoutRedirectUri)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.clientSettings(clientSettings(requirePkce))
				.tokenSettings(tokenSettings())
				.build();
	}

	private ClientSettings clientSettings(boolean requirePkce) {
		return ClientSettings.builder()
				.requireAuthorizationConsent(true)
				.requireProofKey(requirePkce)
				.build();
	}

	private TokenSettings tokenSettings() {
		return TokenSettings.builder().accessTokenTimeToLive(accessTokenTtl).build();
	}

	private ClientSummaryDto toSummary(RegisteredClient client) {
		return new ClientSummaryDto(
				client.getId(),
				client.getClientId(),
				client.getClientName(),
				client.getRedirectUris() != null && !client.getRedirectUris().isEmpty()
						? String.join("\n", client.getRedirectUris())
						: "",
				client.getPostLogoutRedirectUris() != null && !client.getPostLogoutRedirectUris().isEmpty()
						? client.getPostLogoutRedirectUris().iterator().next()
						: null,
				client.getClientSettings().isRequireProofKey());
	}

	/** 解析 redirectUri 文本为列表，支持换行或逗号分隔（与 OAuth2 请求里的值必须完全一致，含查询参数） */
	private static List<String> parseRedirectUris(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split("[\\n,\\r]+"))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
