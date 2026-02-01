package cn.civer.client.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class GlobalErrorController implements ErrorController {

	@RequestMapping("/error")
	public String handleError(jakarta.servlet.http.HttpServletRequest request) {
		Object uriObj = request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI);
		if (uriObj != null) {
			String uri = uriObj.toString();
			if (uri.startsWith("/admin")) {
				return "redirect:/admin/users";
			}
			if (uri.startsWith("/user")) {
				return "redirect:/user/profile";
			}
		}
		return "redirect:/";
	}
}
