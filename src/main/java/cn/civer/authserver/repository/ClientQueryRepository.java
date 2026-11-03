package cn.civer.authserver.repository;

import cn.civer.authserver.dto.ClientSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 客户端注册表的只读投影与级联删除。
 *
 * 为什么不用 {@code RegisteredClientRepository}：列表页只需要 client_id / 名称 /
 * 回调地址 / PKCE 开关，把每行都反序列化成完整 RegisteredClient 再丢掉大部分字段
 * 没有必要；而 PKCE 开关在库上是 client_settings 这段 JSON 里的一个键，必须单独解析。
 *
 * 放进 repository 包是为了让 Service 只表达业务规则，不再拼 SQL、不再认得
 * oauth2_* 的列名。
 */
@Repository
public class ClientQueryRepository {

	private static final Logger log = LoggerFactory.getLogger(ClientQueryRepository.class);

	private static final String LIST_SQL =
			"SELECT id, client_id, client_name, redirect_uris, post_logout_redirect_uris, client_settings "
					+ "FROM oauth2_registered_client ORDER BY client_id";

	/** client_settings 里 PKCE 开关的键名（Spring Authorization Server 的序列化约定） */
	private static final String REQUIRE_PROOF_KEY = "settings.client.require-proof-key";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public ClientQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public List<ClientSummaryDto> findAllSummaries() {
		return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> new ClientSummaryDto(
				rs.getString("id"),
				rs.getString("client_id"),
				rs.getString("client_name"),
				// 库里 redirect_uris 是逗号分隔（JdbcRegisteredClientRepository 的存法），
				// 详情响应是换行分隔。同一个字段两种格式会让前端按 \n 拆分时漏掉多 URI
				// 客户端的「+n」提示，这里统一成换行。
				toLineSeparated(rs.getString("redirect_uris")),
				rs.getString("post_logout_redirect_uris"),
				requireProofKey(rs.getString("client_settings"))));
	}

	/**
	 * 授权、同意、客户端三张表必须一起清掉，否则会留下孤儿授权记录。
	 *
	 * @param registeredClientId 主键（UUID），不是 client_id
	 */
	public void deleteCascade(String registeredClientId) {
		jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", registeredClientId);
		jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?",
				registeredClientId);
		jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", registeredClientId);
	}

	/**
	 * 从 client_settings 的 JSON 里读 PKCE 开关。
	 *
	 * 早期实现用正则匹配 "require-proof-key":true —— 对着 JSON 做正则，格式一变就静默出错，
	 * 且拿不到「明确写了 false」与「压根没这个键」的区别。改为正常解析。
	 *
	 * 注入的是 Jackson 3（tools.jackson）的 ObjectMapper：Spring Boot 4 已把默认 JSON
	 * 实现迁到 Jackson 3，com.fasterxml 的 ObjectMapper 不在容器里，注入会直接启动失败。
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

	/** 逗号分隔 → 换行分隔，空值统一成空串（模板按 isEmpty 判断） */
	private static String toLineSeparated(String raw) {
		if (!StringUtils.hasText(raw)) {
			return "";
		}
		return String.join("\n", Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList());
	}
}
