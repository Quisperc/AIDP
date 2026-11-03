package cn.civer.authserver.controller;

import cn.civer.authserver.dto.ClientDto;
import cn.civer.authserver.dto.ClientSummaryDto;
import cn.civer.authserver.dto.PageResult;
import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.service.AuditService;
import cn.civer.authserver.service.ClientService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理台 · 客户端管理（服务端渲染）。
 *
 * 与用户管理同样走 PRG。这里多一条规则：编辑时 Client Secret 留空表示不修改，
 * 因为库里存的是 BCrypt 哈希，回显不了原文——把哈希回填进表单再提交一次，
 * 等于用一个哈希当明文密钥重新加密，客户端立刻登不上（历史上踩过）。
 *
 * 删除与改密钥都会写审计：这两类操作不可恢复，必须能回答「谁在什么时候做的」。
 */
@Controller
@RequestMapping("/admin/clients")
public class AdminClientController {

	private final ClientService clientService;
	private final AuditService auditService;

	public AdminClientController(ClientService clientService, AuditService auditService) {
		this.clientService = clientService;
		this.auditService = auditService;
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "0") int page, Model model) {
		PageResult<ClientSummaryDto> pager = clientService.listClients(page, PageResult.DEFAULT_SIZE);
		model.addAttribute("pager", pager);
		model.addAttribute("clients", pager.items());
		model.addAttribute("active", "clients");
		return "admin/clients";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("active", "clients");
		model.addAttribute("actionPath", "/admin/clients");
		return "admin/client-form";
	}

	@PostMapping
	public String create(@RequestParam String clientId,
			@RequestParam(required = false) String clientName,
			@RequestParam String clientSecret,
			@RequestParam(required = false) String redirectUri,
			@RequestParam(required = false) String postLogoutRedirectUri,
			@RequestParam(defaultValue = "true") boolean requirePkce,
			RedirectAttributes ra) {
		try {
			clientService.register(new ClientDto(clientId.trim(), clientSecret, redirectUri,
					postLogoutRedirectUri, clientName, requirePkce));
			auditService.record(AuditService.CLIENT_CREATE, clientId.trim(),
					"redirectUri=" + redirectUri + ", requirePkce=" + requirePkce);
			ra.addFlashAttribute("ok", "已注册客户端「" + clientId.trim() + "」");
			return "redirect:/admin/clients";
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			// 失败时把已填内容带回去，别让管理员重填一遍
			ra.addFlashAttribute("form", new ClientDto(clientId, null, redirectUri,
					postLogoutRedirectUri, clientName, requirePkce));
			return "redirect:/admin/clients/new";
		}
	}

	@GetMapping("/{clientId}/edit")
	public String editForm(@PathVariable String clientId, Model model, RedirectAttributes ra) {
		try {
			model.addAttribute("client", clientService.getClient(clientId));
			model.addAttribute("active", "clients");
			model.addAttribute("actionPath", "/admin/clients/" + clientId);
			return "admin/client-form";
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/clients";
		}
	}

	@PostMapping("/{clientId}")
	public String update(@PathVariable String clientId,
			@RequestParam(required = false) String clientName,
			@RequestParam(required = false) String clientSecret,
			@RequestParam(required = false) String redirectUri,
			@RequestParam(required = false) String postLogoutRedirectUri,
			@RequestParam(defaultValue = "false") boolean requirePkce,
			RedirectAttributes ra) {
		try {
			// clientSecret 留空 = 不改密钥，Service 会沿用库里的哈希
			boolean secretRotated = clientSecret != null && !clientSecret.isBlank();
			clientService.update(clientId, new ClientDto(clientId, clientSecret, redirectUri,
					postLogoutRedirectUri, clientName, requirePkce));
			auditService.record(AuditService.CLIENT_UPDATE, clientId,
					"redirectUri=" + redirectUri + ", requirePkce=" + requirePkce
							+ ", secretRotated=" + secretRotated);
			ra.addFlashAttribute("ok", "已保存客户端「" + clientId + "」");
			return "redirect:/admin/clients";
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/clients/" + clientId + "/edit";
		}
	}

	@PostMapping("/{clientId}/delete")
	public String delete(@PathVariable String clientId, RedirectAttributes ra) {
		try {
			clientService.delete(clientId);
			auditService.record(AuditService.CLIENT_DELETE, clientId, "cascade=authorizations+consents");
			ra.addFlashAttribute("ok", "已删除客户端「" + clientId + "」及其授权记录");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/clients";
	}
}
