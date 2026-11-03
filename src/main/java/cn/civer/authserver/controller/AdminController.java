package cn.civer.authserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理台入口：/admin 直接落到用户列表。
 */
@Controller
public class AdminController {

	@GetMapping("/admin")
	public String index() {
		return "redirect:/admin/users";
	}
}
