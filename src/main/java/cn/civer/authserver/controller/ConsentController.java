package cn.civer.authserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 授权同意页。
 *
 * 只负责把「哪个客户端、请求了哪些 scope」渲染出来；真正的同意结果由
 * Spring Authorization Server 的 /oauth2/authorize 处理。
 *
 * 客户端不存在时不能只报 400：用户看到的应该是「请联系管理员在认证中心配置」，
 * 所以带上可读消息跳错误页。
 */
@Controller
public class ConsentController {

	private static final Logger log = LoggerFactory.getLogger(ConsentController.class);
	private static final String CLIENT_NOT_FOUND_MESSAGE =
			"该客户端未在认证中心注册或已失效，请联系系统管理员在认证中心重新配置后再试。";

	private final RegisteredClientRepository registeredClientRepository;

	public ConsentController(RegisteredClientRepository registeredClientRepository) {
		this.registeredClientRepository = registeredClientRepository;
	}

	@GetMapping(value = "/oauth2/consent")
	public String consent(Principal principal, Model model,
			@RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
			@RequestParam(OAuth2ParameterNames.SCOPE) String scope,
			@RequestParam(OAuth2ParameterNames.STATE) String state) {

		if (principal == null) {
			// 未登录就没有「同意」的主体，回登录页比渲染一张空名单更清楚
			log.warn("[consent] unauthenticated consent request: clientId={}", clientId);
			return "redirect:/login";
		}

		RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
		if (registeredClient == null) {
			log.warn("[consent] client not found, redirecting to error page: clientId={}", clientId);
			return "redirect:/error?message=" + URLEncoder.encode(CLIENT_NOT_FOUND_MESSAGE, StandardCharsets.UTF_8);
		}

		// 当前实现是「凡请求的 scope 都展示、都默认勾选」。请求的 scope 已在授权端点
		// 由框架按客户端注册范围校验过（openid / profile / email），这里只负责渲染。
		// 将来需要「记住用户上次勾选」时，再结合 OAuth2AuthorizationConsentService
		// 过滤掉已授予的 scope。
		Set<String> scopesToApprove = Arrays
				.stream(StringUtils.delimitedListToStringArray(scope, " "))
				.filter(StringUtils::hasText)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		model.addAttribute("clientId", clientId);
		model.addAttribute("state", state);
		model.addAttribute("scopes", scopesToApprove);
		model.addAttribute("principalName", principal.getName());
		model.addAttribute("clientName", registeredClient.getClientName() != null
				? registeredClient.getClientName()
				: registeredClient.getClientId());

		return "consent";
	}
}
