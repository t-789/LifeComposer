package org.example.lifecomposer.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/admin")
    public String adminMain(Authentication authentication) {
        if (isAdmin(authentication)) {
            return "admin_main";
        }
        return "redirect:/error/403";
    }

    @GetMapping("/admin/user")
    public String userManagement(Authentication authentication) {
        if (isAdmin(authentication)) {
            return "user_management";
        }
        return "redirect:/error/403";
    }

    @GetMapping("/admin/feedback_management")
    public String feedbackManagement(Authentication authentication) {
        if (isAdmin(authentication)) {
            return "feedback_management";
        }
        return "redirect:/error/403";
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
