package cn.civer.authserver.service;

import cn.civer.authserver.dto.ClientDto;
import cn.civer.authserver.dto.ClientSummaryDto;
import cn.civer.authserver.dto.PageResult;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.repository.ClientQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 客户端（RegisteredClient）业务逻辑。
 *
 * 原先这些逻辑全在 RegisteredClientController 里：拼接 RegisteredClient.Builder、
 * 手写多条 DELETE、直接查 oauth2_registered_client 表。Controller 因此既管协议又管业务，
 * 加任何规则都只能继续往里堆。这里把它们收拢，Controller 只负责校验入参与组装响应。
 *
 * 列表投影与级联删除下沉到 {@link ClientQueryRepository}，本类只表达业务规则。
 */
@Service
public class ClientService {

	private static final Logger log = LoggerFactory.getLogger(ClientService.class);

	private final RegisteredClientRepository registeredClientRepository;
	private final ClientQueryRepository clientQueryRepository;
	private final PasswordEncoder passwordEncoder;
	private final Duration accessTokenTtl;

	public ClientService(RegisteredClientRepository registeredClientRepository,
			ClientQueryRepository clientQueryRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.auth.access-token-time-to-live:30m}") Duration accessTokenTtl) {
		this.registeredClientRepository = registeredClientRepository;
		this.clientQueryRepository = clientQueryRepository;
		this.passwordEncoder = passwordEncoder;
		this.accessTokenTtl = accessTokenTtl;
	}

	public PageResult<ClientSummaryDto> listClients(int page, int size) {
		return clientQueryRepository.findSummaries(page, size);
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

		RegisteredClient.Builder builder = baseBuilder(UUID.randomUUID().toString(), dto.clientId(),
				passwordEncoder.encode(dto.clientSecret()), displayName(dto))
				.clientSettings(clientSettings(dto.requirePkce() == null || dto.requirePkce()))
				.tokenSettings(tokenSettings());
		redirectUris.forEach(builder::redirectUri);
		// 留空就不写这一项。此前无条件调用 postLogoutRedirectUri(null)，
		// 库里会落下一个字符串 "null"，编辑页把它回显出来，看起来像配了个垃圾地址。
		if (StringUtils.hasText(dto.postLogoutRedirectUri())) {
			builder.postLogoutRedirectUri(dto.postLogoutRedirectUri().trim());
		}
		registeredClientRepository.save(builder.build());

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
			// 表单留空 = 沿用原值；不这样兜底会把客户端的回调地址清空
			redirectUris = existing.getRedirectUris().stream().toList();
		}
		boolean pkce = dto.requirePkce() != null ? dto.requirePkce() : existing.getClientSettings().isRequireProofKey();
		String secret = StringUtils.hasText(dto.clientSecret())
				? passwordEncoder.encode(dto.clientSecret())
				: existing.getClientSecret();
		String name = StringUtils.hasText(dto.clientName()) ? dto.clientName() : existing.getClientName();

		RegisteredClient.Builder builder = baseBuilder(existing.getId(), existing.getClientId(), secret, name)
				.clientSettings(clientSettings(pkce))
				.tokenSettings(tokenSettings());
		redirectUris.forEach(builder::redirectUri);
		// 只有显式填写才覆盖；否则保留库里全部 post-logout URI。
		// 早先只取 iterator().next() 再写回，多值客户端改一次名称就只剩一条，属于静默丢数据。
		List<String> postLogoutUris = StringUtils.hasText(dto.postLogoutRedirectUri())
				? List.of(dto.postLogoutRedirectUri().trim())
				: new ArrayList<>(existing.getPostLogoutRedirectUris());
		postLogoutUris.stream()
				.filter(StringUtils::hasText)
				.forEach(builder::postLogoutRedirectUri);

		registeredClientRepository.save(builder.build());
		log.info("[clients] updated: clientId={}", clientId);
	}

	@Transactional
	public void delete(String clientId) {
		RegisteredClient client = registeredClientRepository.findByClientId(clientId);
		if (client == null) {
			throw BusinessException.notFound("客户端不存在: " + clientId);
		}
		clientQueryRepository.deleteCascade(client.getId());
		log.info("[clients] deleted: clientId={}", clientId);
	}

	/** 注册与更新共用的骨架：认证方式、授权类型、scope、有效期。 */
	private RegisteredClient.Builder baseBuilder(String id, String clientId, String clientSecret, String clientName) {
		return RegisteredClient.withId(id)
				.clientId(clientId)
				.clientSecret(clientSecret)
				.clientName(clientName)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.scope(OidcScopes.EMAIL);
	}

	/**
	 * 显示名称留空时退回 client_id。
	 * 表上 client_name 是 NOT NULL，为空会在 insert 时抛异常变成 500；
	 * 而这个字段在表单里本来就是可选的。
	 */
	private static String displayName(ClientDto dto) {
		return StringUtils.hasText(dto.clientName()) ? dto.clientName().trim() : dto.clientId().trim();
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
						? String.join(",", client.getRedirectUris())
						: "",
				client.getPostLogoutRedirectUris() != null && !client.getPostLogoutRedirectUris().isEmpty()
						? client.getPostLogoutRedirectUris().iterator().next()
						: null,
				client.getClientSettings().isRequireProofKey());
	}

	/** 解析逗号分隔的 redirect_uris（与框架入库格式一致；与 OAuth2 请求值必须逐字符一致） */
	private static List<String> parseRedirectUris(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
