package cn.civer.authserver.repository;

import cn.civer.authserver.dto.AuthorizationView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户级授权视图与单客户端撤销。
 *
 * 现有 JdbcOAuth2AuthorizationConsentService 只有 findById(clientId, principal)，
 * 没有「按主体列出」的能力，而账号页需要它；这类只读投影直接用 JdbcTemplate，
 * 与 ClientQueryRepository 的做法一致。
 *
 * 撤销必须同时删 consent 与 authorization：consent 只决定下次是否重弹同意页，
 * 已发出的 refresh_token 能否续期由 authorization 决定（见 SsoLogoutRevocationTest）。
 */
@Repository
public class AuthorizationQueryRepository {

	private static final String LIST_SQL = """
			SELECT r.client_id, r.client_name, c.authorities,
			       (SELECT COUNT(*) FROM oauth2_authorization a
			         WHERE a.principal_name = c.principal_name
			           AND a.registered_client_id = c.registered_client_id) AS token_count
			  FROM oauth2_authorization_consent c
			  JOIN oauth2_registered_client r ON r.id = c.registered_client_id
			 WHERE c.principal_name = ?
			 ORDER BY r.client_id
			""";

	private final JdbcTemplate jdbcTemplate;

	public AuthorizationQueryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<AuthorizationView> listForPrincipal(String principalName) {
		return jdbcTemplate.query(LIST_SQL, (rs, rowNum) -> new AuthorizationView(
				rs.getString("client_id"),
				rs.getString("client_name"),
				scopeNames(rs.getString("authorities")),
				rs.getLong("token_count")), principalName);
	}

	/**
	 * 只撤销「当前用户 + 指定客户端」的授权。
	 *
	 * 先按 client_id 与 principal_name 关联查内部 UUID，查不到就返回 0：
	 * 这同时挡住了「撤销别人/不存在客户端」的越权与探测。
	 * 返回被删除的 authorization 条数，供上层判断是否真的撤销成功。
	 */
	public int revokeClient(String principalName, String clientId) {
		List<String> internalIds = jdbcTemplate.queryForList(
				"SELECT r.id FROM oauth2_registered_client r "
						+ "JOIN oauth2_authorization_consent c ON c.registered_client_id = r.id "
						+ "WHERE r.client_id = ? AND c.principal_name = ?",
				String.class, clientId, principalName);
		if (internalIds.isEmpty()) {
			return 0;
		}
		String internalId = internalIds.get(0);
		jdbcTemplate.update("DELETE FROM oauth2_authorization_consent "
				+ "WHERE principal_name = ? AND registered_client_id = ?", principalName, internalId);
		return jdbcTemplate.update("DELETE FROM oauth2_authorization "
				+ "WHERE principal_name = ? AND registered_client_id = ?", principalName, internalId);
	}

	/** consent.authorities 里存的是 SCOPE_openid 这类权限名，转成可读的 scope 列表 */
	private static String scopeNames(String authorities) {
		if (authorities == null || authorities.isBlank()) {
			return "";
		}
		return Arrays.stream(authorities.split(","))
				.map(String::trim)
				.filter(value -> value.startsWith("SCOPE_"))
				.map(value -> value.substring("SCOPE_".length()))
				.collect(Collectors.joining(", "));
	}
}
