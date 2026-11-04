package cn.civer.authserver.service;

import cn.civer.authserver.dto.AuditView;
import cn.civer.authserver.dto.PageResult;
import cn.civer.authserver.entity.AdminAuditLog;
import cn.civer.authserver.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * 管理台操作审计。
 *
 * 设计取舍：
 * - **不让调用方传 actor/ip**：这两项只能从当前请求推导，开放成参数迟早有人传错或传空。
 * - **独立事务（REQUIRES_NEW）**：记录动作发生在业务成功之后，若与业务共用事务，
 *   调用方一旦回滚，留痕也会一起消失；独立事务保证「看到的记录 = 真实发生过的事」。
 * - **只记录成功完成的操作**：失败的操作没有改变任何状态，记进来只会让审计页变成
 *   错误日志的副本；失败原因去服务端日志里查。
 * - 审计失败只记日志、不抛出：留痕是附属能力，不该成为主流程的故障点。
 *
 * 动作码集中在这里，避免各处手写字符串导致同一动作出现多种拼写。
 */
@Service
public class AuditService {

	public static final String USER_CREATE = "USER_CREATE";
	public static final String USER_UPDATE = "USER_UPDATE";
	public static final String USER_DISABLE = "USER_DISABLE";
	public static final String CLIENT_CREATE = "CLIENT_CREATE";
	public static final String CLIENT_UPDATE = "CLIENT_UPDATE";
	public static final String CLIENT_DELETE = "CLIENT_DELETE";
	public static final String ACCOUNT_UPDATE = "ACCOUNT_UPDATE";
	public static final String AUTHZ_REVOKE = "AUTHZ_REVOKE";

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);
	private static final int MAX_TARGET_LENGTH = 255;
	private static final int MAX_DETAIL_LENGTH = 1000;

	private final AdminAuditLogRepository repository;

	public AuditService(AdminAuditLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String action, String target, String detail) {
		try {
			repository.save(new AdminAuditLog(
					currentActor(), action, truncate(target, MAX_TARGET_LENGTH),
					truncate(detail, MAX_DETAIL_LENGTH), currentIp(), Instant.now()));
		} catch (Exception e) {
			log.warn("[audit] 记录失败（不影响业务）: action={}, target={}, reason={}",
					action, target, e.getMessage());
		}
	}

	public PageResult<AuditView> list(int page, int size) {
		long total = repository.count();
		int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
		int safePage = total == 0 ? 0 : Math.min(Math.max(page, 0), totalPages - 1);
		var result = repository.findAllByOrderByIdDesc(PageRequest.of(safePage, size));
		return PageResult.of(result.getContent().stream().map(AuditView::from).toList(), safePage, size, total);
	}

	private static String currentActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return "unknown";
		}
		return authentication.getName();
	}

	/**
	 * 取真实来源 IP：反代后 remoteAddr 恒为网关地址，必须优先看 X-Forwarded-For。
	 * 只取第一个（最靠近客户端的）地址，且只用于留痕，不作为鉴权依据。
	 */
	private static String currentIp() {
		HttpServletRequest request = currentRequest();
		if (request == null) {
			return null;
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			String first = forwarded.split(",")[0].trim();
			return truncate(first, 64);
		}
		return truncate(request.getRemoteAddr(), 64);
	}

	private static HttpServletRequest currentRequest() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest();
		}
		return null;
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
