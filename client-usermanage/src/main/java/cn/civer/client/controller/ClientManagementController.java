package cn.civer.client.controller;

import cn.civer.client.client.ClientFeignClient;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/clients")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ClientManagementController {

	private static final Logger log = LoggerFactory.getLogger(ClientManagementController.class);
	private final ClientFeignClient clientFeignClient;

	public ClientManagementController(ClientFeignClient clientFeignClient) {
		this.clientFeignClient = clientFeignClient;
	}

	@GetMapping
	public String listClients(Model model) {
		try {
			List<ClientFeignClient.ClientSummaryDto> clients = clientFeignClient.listClients();
			model.addAttribute("clients", clients);
			log.debug("[admin/clients] list size={}", clients != null ? clients.size() : 0);
		} catch (Exception e) {
			log.warn("[admin/clients] list failed: {}", e.getMessage());
			model.addAttribute("error", "加载客户端列表失败: " + toFriendlyMessage(e));
			model.addAttribute("clients", List.<ClientFeignClient.ClientSummaryDto>of());
		}
		return "clients";
	}

	@GetMapping("/edit")
	public String editForm(@RequestParam String clientId, Model model, RedirectAttributes redirectAttributes) {
		try {
			ClientFeignClient.ClientSummaryDto client = clientFeignClient.getClient(clientId);
			model.addAttribute("client", client);
			log.debug("[admin/clients] edit form clientId={}", clientId);
			return "client-edit";
		} catch (Exception e) {
			log.warn("[admin/clients] edit form failed clientId={}: {}", clientId, e.getMessage());
			redirectAttributes.addFlashAttribute("error", "客户端不存在或加载失败: " + toFriendlyMessage(e));
			return "redirect:/admin/clients";
		}
	}

	@PostMapping
	public String addClient(@RequestParam String clientId,
			@RequestParam String clientSecret,
			@RequestParam String redirectUri,
			@RequestParam String postLogoutRedirectUri,
			@RequestParam String clientName,
			RedirectAttributes redirectAttributes) {
		ClientFeignClient.ClientDto dto = new ClientFeignClient.ClientDto();
		dto.clientId = clientId;
		dto.clientSecret = clientSecret;
		dto.redirectUri = redirectUri;
		dto.postLogoutRedirectUri = postLogoutRedirectUri;
		dto.clientName = clientName;

		try {
			clientFeignClient.registerClient(dto);
			redirectAttributes.addFlashAttribute("message", "客户端注册成功！");
			log.info("[admin/clients] registered clientId={}", clientId);
		} catch (Exception e) {
			log.warn("[admin/clients] register failed clientId={}: {}", clientId, e.getMessage());
			redirectAttributes.addFlashAttribute("error", "注册失败: " + toFriendlyMessage(e));
		}
		return "redirect:/admin/clients";
	}

	@PostMapping("/edit")
	public String updateClient(@RequestParam String clientId,
			@RequestParam(required = false) String clientSecret,
			@RequestParam String redirectUri,
			@RequestParam String postLogoutRedirectUri,
			@RequestParam String clientName,
			RedirectAttributes redirectAttributes) {
		ClientFeignClient.ClientDto dto = new ClientFeignClient.ClientDto();
		dto.clientId = clientId;
		dto.clientSecret = (clientSecret != null && !clientSecret.isBlank()) ? clientSecret : null;
		dto.redirectUri = redirectUri;
		dto.postLogoutRedirectUri = postLogoutRedirectUri;
		dto.clientName = clientName;
		try {
			clientFeignClient.updateClient(clientId, dto);
			redirectAttributes.addFlashAttribute("message", "客户端更新成功！");
			log.info("[admin/clients] updated clientId={}", clientId);
		} catch (Exception e) {
			log.warn("[admin/clients] update failed clientId={}: {}", clientId, e.getMessage());
			redirectAttributes.addFlashAttribute("error", "更新失败: " + toFriendlyMessage(e));
		}
		return "redirect:/admin/clients";
	}

	@PostMapping("/delete")
	public String deleteClient(@RequestParam String clientId, RedirectAttributes redirectAttributes) {
		try {
			clientFeignClient.deleteClient(clientId);
			redirectAttributes.addFlashAttribute("message", "客户端已删除。");
			log.info("[admin/clients] deleted clientId={}", clientId);
		} catch (Exception e) {
			log.warn("[admin/clients] delete failed clientId={}: {}", clientId, e.getMessage());
			redirectAttributes.addFlashAttribute("error", "删除失败: " + toFriendlyMessage(e));
		}
		return "redirect:/admin/clients";
	}

	/**
	 * 将接口异常转为对用户友好的中文提示。
	 */
	private static String toFriendlyMessage(Throwable e) {
		String msg = e.getMessage();
		if (msg == null) {
			return "请稍后重试。";
		}
		// Feign 返回体可能包含 JSON，尝试提取可读信息
		if (e instanceof FeignException feignEx) {
			int status = feignEx.status();
			if (status == 409) {
				return "该 Client ID 已被使用，请换一个。";
			}
			if (status == 404) {
				return "客户端不存在或已失效。";
			}
			String body = feignEx.contentUTF8();
			if (body != null && body.contains("already exists")) {
				return "该 Client ID 已被使用，请换一个。";
			}
			if (body != null && (body.contains("not found") || body.contains("Client not found"))) {
				return "客户端不存在或已失效。";
			}
		}
		if (msg.contains("already exists") || msg.contains("Client ID already exists")) {
			return "该 Client ID 已被使用，请换一个。";
		}
		if (msg.contains("not found") || msg.contains("Client not found")) {
			return "客户端不存在或已失效。";
		}
		// 避免把整段 HTTP 错误原文展示给用户
		if (msg.contains("Internal Error") || msg.contains("during [POST]") || msg.contains("during [GET]") || msg.contains("during [PUT]") || msg.contains("during [DELETE]")) {
			return "认证中心暂时不可用，请稍后重试。";
		}
		return msg.length() > 120 ? msg.substring(0, 120) + "…" : msg;
	}
}
