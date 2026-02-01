package cn.civer.client.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home(org.springframework.security.core.Authentication authentication,
			org.springframework.ui.Model model) {
		// Just return the dashboard. Thymeleaf + Security Extras will handle
		// visibility.
		return "index";
	}

}
