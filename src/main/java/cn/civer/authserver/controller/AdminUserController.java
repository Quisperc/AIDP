package cn.civer.authserver.controller;

import cn.civer.authserver.exception.BusinessException;
import cn.civer.authserver.service.UserAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理台 · 用户管理（服务端渲染）。
 *
 * 形状是标准的 PRG：GET 出页面，POST 做事，然后 302 回列表，
 * 避免刷新重复提交。失败不抛到错误页，而是把消息塞进 flash 回到原页面——
 * 管理员填错一个用户名字就被整页错误打断，体验很差。
 *
 * 权限在 SecurityConfig 里按 /admin/** 统一要求 ROLE_ADMIN，
 * 方法上不再逐个加 @PreAuthorize（那是 REST 时代为 URL 粒度不足做的补偿）。
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

	private final UserAdminService userAdminService;

	public AdminUserController(UserAdminService userAdminService) {
		this.userAdminService = userAdminService;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("users", userAdminService.listUsers());
		model.addAttribute("active", "users");
		return "admin/users";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("active", "users");
		model.addAttribute("actionPath", "/admin/users");
		return "admin/user-form";
	}

	@PostMapping
	public String create(@RequestParam String username,
			@RequestParam String password,
			@RequestParam(defaultValue = "ROLE_USER") String role,
			RedirectAttributes ra) {
		try {
			userAdminService.create(username, password, role);
			ra.addFlashAttribute("ok", "已创建用户「" + username.trim() + "」");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			ra.addFlashAttribute("username", username);
			ra.addFlashAttribute("role", role);
		}
		return "redirect:/admin/users";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
		try {
			model.addAttribute("user", userAdminService.get(id));
			model.addAttribute("active", "users");
			model.addAttribute("actionPath", "/admin/users/" + id);
			return "admin/user-form";
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/users";
		}
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id,
			@RequestParam(defaultValue = "ROLE_USER") String role,
			@RequestParam(defaultValue = "false") boolean enabled,
			@RequestParam(required = false) String password,
			RedirectAttributes ra) {
		try {
			userAdminService.update(id, role, enabled, password);
			ra.addFlashAttribute("ok", "已保存");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/users";
	}

	@PostMapping("/{id}/delete")
	public String disable(@PathVariable Long id, RedirectAttributes ra) {
		try {
			userAdminService.disable(id);
			ra.addFlashAttribute("ok", "已停用该用户（保留授权记录，可再次启用）");
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/users";
	}
}
