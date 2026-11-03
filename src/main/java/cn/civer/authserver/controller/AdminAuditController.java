package cn.civer.authserver.controller;

import cn.civer.authserver.dto.PageResult;
import cn.civer.authserver.dto.AuditView;
import cn.civer.authserver.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理台 · 操作审计。
 *
 * 只读页面：审计记录不提供编辑与删除入口（否则「留痕」这件事就没有意义）。
 * 时间按服务器时区展示，库里统一存 UTC。
 */
@Controller
@RequestMapping("/admin/audit")
public class AdminAuditController {

	private final AuditService auditService;

	public AdminAuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	public String list(@RequestParam(defaultValue = "0") int page, Model model) {
		PageResult<AuditView> pager = auditService.list(page, PageResult.DEFAULT_SIZE);
		model.addAttribute("pager", pager);
		model.addAttribute("logs", pager.items());
		model.addAttribute("active", "audit");
		return "admin/audit";
	}
}
