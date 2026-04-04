package org.example.lifecomposer.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.entity.User;
import org.example.lifecomposer.service.FeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class CustomErrorController {

    private final UserRepository userRepository;
    private final FeedbackService feedbackService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CustomErrorController(UserRepository userRepository, FeedbackService feedbackService) {
        this.userRepository = userRepository;
        this.feedbackService = feedbackService;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Integer userId = null;
        String username = "Anonymous";

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String authUsername = authentication.getName();
            User user = userRepository.findByUsername(authUsername);
            if (user != null) {
                userId = user.getId();
                username = user.getUsername();
            }
        }

        if (status != null) {
            try {
                HttpStatus httpStatus = HttpStatus.valueOf(Integer.parseInt(status.toString()));

                if (httpStatus.is5xxServerError()) {
                    final Integer finalUserId = userId;
                    final String finalUsername = username;
                    final String content = "HTTP " + status + ": " + (errorMessage != null ? errorMessage : "未知错误");
                    final String url = requestUri != null ? requestUri.toString() : request.getRequestURI();
                    final String userAgent = request.getHeader("User-Agent");
                    final String stackTrace = buildStackTrace(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION), status, errorMessage);

                    executorService.submit(() -> feedbackService.saveSystemFeedback(finalUserId, finalUsername, content, url, userAgent, stackTrace));
                }
            } catch (Exception ignored) {
                // keep error handler itself resilient
            }
        }

        model.addAttribute("statusCode", status);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("requestUri", requestUri);

        if (status != null) {
            try {
                int statusCode = Integer.parseInt(status.toString());
                return switch (statusCode) {
                    case 403 -> "error/403";
                    case 404 -> "error/404";
                    case 500 -> "error/500";
                    default -> "error/general";
                };
            } catch (NumberFormatException ignored) {
            }
        }

        return "error/general";
    }

    @RequestMapping("/error/403")
    public String handle403() {
        return "error/403";
    }

    private String buildStackTrace(Object exception, Object status, Object errorMessage) {
        if (exception instanceof Throwable t) {
            StringBuilder sb = new StringBuilder();
            sb.append("Exception: ").append(t.getClass().getName()).append("\n");
            sb.append("Message: ").append(t.getMessage()).append("\n");
            sb.append("Stack Trace:\n");
            for (StackTraceElement element : t.getStackTrace()) {
                sb.append("  at ").append(element).append("\n");
            }
            return sb.toString();
        }
        return "Status: " + status + ", Message: " + errorMessage;
    }
}
