package org.example.lifecomposer.Exception;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import java.util.Set;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    private static final Set<String> BOT_USER_AGENTS = Set.of(
            "bot", "crawler", "spider", "scraper", "python", "wget"
    );

    private static final Set<String> IGNORED_NOT_FOUND_URLS = Set.of(
            "/json/", "/squid-internal-mgr/cachemgr.cgi", "/board.cgi",
            "/login.asp", "/SDK/webLanguage", "/sitemap.xml", "/ip", "/download/powershell/", "/get.php",
            "/wiki", "/bins/", "/bin/", "/backup/", "/cgi-bin/authLogin.cgi", "/WuEL", "/a", "/SiteLoader",
            "/mPlayer", "/geoserver/web/", "/rpcform/login", "/css/images/PTZOptics_powerby.png", "/showLogin.cc",
            "/helpdesk/WebObjects/Helpdesk.woa", "/static/historypage.js", "/zabbix/favicon.ico", "/WebInterface/",
            "/Telerik.Web.UI.WebResource.axd", "/partymgr/control/main", "/version", "/owncloud/status.php",
            "/status.php", "/license.txt", "/wp-json", "/ssi.cgi/Login.htm", "/console", "/webfig/",
            "/jasperserver/login.html", "/jasperserver-pro/login.html", "/jasperserverTest/login.html",
            "/cgi-bin/main.pl", "/js/NewWindow_2_all.js", "/hudson", "/images/js/eas/eas.js", "/.env",
            "/.git/config", "/misc.php", "/bbs/misc.php", "/1.php", "/nmaplowercheck1766405951",
            "/sdk", "/HNAP1", "/.well-known/security.txt"
    );

    private final FeedbackService feedbackService;

    public GlobalExceptionHandler(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex, WebRequest request) {
        if (isBotRequest(request.getHeader("User-Agent"))) {
            logger.warn("Bot request: {}", request.getDescription(false));
            return ResponseEntity.status(403).body("Forbidden");
        }

        if (ex instanceof NoResourceFoundException) {
            if (IGNORED_NOT_FOUND_URLS.stream().noneMatch(url -> request.getDescription(false).contains(url))) {
                logger.warn("Resource not found: {}", request.getDescription(false));
            }
            return ResponseEntity.notFound().build();
        }

        if (ex instanceof HttpRequestMethodNotSupportedException) {
            logger.warn("Method not supported: {}", request.getDescription(false));
            return ResponseEntity.notFound().build();
        }

        if (ex instanceof org.springframework.web.HttpMediaTypeNotSupportedException) {
            logger.warn("Media type not supported: {}", request.getDescription(false));
            return ResponseEntity.status(400).body("Unsupported media type");
        }

        if (ex instanceof ConstraintViolationException) {
            logger.warn("Constraint violation: {}", request.getDescription(false));
            return ResponseEntity.badRequest().body("Invalid input");
        }

        if (ex instanceof MethodArgumentTypeMismatchException) {
            logger.warn("Method argument type mismatch: {}", request.getDescription(false));
            return ResponseEntity.badRequest().body("Method argument type mismatch");
        }

        if (ex instanceof org.springframework.web.bind.MethodArgumentNotValidException) {
            logger.warn("Method argument not valid: {}", request.getDescription(false));
            return ResponseEntity.badRequest().body("Method argument not valid.");
        }

        if (ex instanceof org.springframework.web.bind.MissingServletRequestParameterException) {
            logger.warn("Missing servlet request parameter: {}", request.getDescription(false));
            return ResponseEntity.badRequest().body("Missing servlet request parameter.");
        }

        if (ex instanceof org.springframework.security.authentication.InternalAuthenticationServiceException) {
            logger.warn("InternalAuthenticationServiceException: {}", request.getDescription(false));
            return ResponseEntity.status(500).body("Internal Server Error");
        }

        if (ex instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
            logger.warn("AsyncRequestNotUsableException occurred. {}", request.getDescription(false));
            return ResponseEntity.status(500).body("Internal Server Error");
        }

        logger.error("Unhandled exception occurred: ", ex);

        try {
            feedbackService.saveSystemFeedback(
                    null,
                    "system",
                    ex.getMessage(),
                    request.getDescription(false),
                    request.getHeader("User-Agent"),
                    java.util.Arrays.toString(ex.getStackTrace())
            );
        } catch (Exception e) {
            logger.error("Failed to save system feedback: ", e);
        }

        return ResponseEntity.status(500).body("Internal Server Error");
    }

    private boolean isBotRequest(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return true;
        String ua = userAgent.toLowerCase();
        return BOT_USER_AGENTS.stream().anyMatch(ua::contains);
    }
}
