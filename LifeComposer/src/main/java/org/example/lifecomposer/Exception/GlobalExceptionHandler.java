package org.example.lifecomposer.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Service.FeedbackService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.Set;

/**
 * Global fallback for routing errors, server failures and system-error feedback.
 *
 * <p>Responsibility boundary (v0.0.7 audit remediation): this advice owns only
 * exceptions that no narrower handler claims — unknown paths, unsupported HTTP
 * methods, authentication-provider failures, client disconnects, and the final
 * {@code Exception} fallback that records system feedback. Client input errors
 * (Bean Validation, body parsing, type conversion, missing parameters and
 * unsupported media types) belong to {@link ValidationExceptionHandler}.
 *
 * <p>Ordered {@link Ordered#LOWEST_PRECEDENCE} so an exact handler always wins
 * over the {@code Exception.class} catch-all, regardless of bean registration
 * order. The bot policy is shared with the input-error advice through
 * {@link BotRequestGuard}.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * 499 is the de-facto "client closed request" status. It is not a business
     * input error and must not be reported as a successful response.
     */
    private static final HttpStatusCode CLIENT_CLOSED_REQUEST = HttpStatusCode.valueOf(499);

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

    /** Unknown path: API gets a bare 404, pages keep the existing redirect contract. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleNoResourceFound(NoResourceFoundException ex,
                                                        WebRequest request,
                                                        HttpServletRequest httpRequest) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(httpRequest);
        if (botResponse != null) {
            return botResponse;
        }
        if (IGNORED_NOT_FOUND_URLS.stream().noneMatch(url -> request.getDescription(false).contains(url))) {
            logger.warn("Resource not found: {}", request.getDescription(false));
        }
        return routeMissingResource(httpRequest);
    }

    /** Unsupported HTTP method keeps the historical API 404 / page redirect contract. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<String> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                           WebRequest request,
                                                           HttpServletRequest httpRequest) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(httpRequest);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("Method not supported: {}", request.getDescription(false));
        return routeMissingResource(httpRequest);
    }

    /**
     * The client closed the connection before the response was written, so it can
     * never see this status; 499 keeps it out of both the "business input error"
     * bucket (it used to be a 400) and the "successful response" bucket (a bare
     * null handler would have produced an empty 200). It deliberately does not
     * write system feedback.
     */
    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<String> handleClientAbort(ClientAbortException ex,
                                                    WebRequest request,
                                                    HttpServletRequest httpRequest) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(httpRequest);
        if (botResponse != null) {
            return botResponse;
        }
        logger.debug("Client aborted before the response was completed: {}", request.getDescription(false));
        return ResponseEntity.status(CLIENT_CLOSED_REQUEST).body("ClientAbortException");
    }

    /**
     * The async request can no longer be written to (typically the client is
     * gone). It keeps the historical 500 server-error contract but never writes
     * system feedback: a disconnect is not an application defect.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<String> handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex,
                                                              WebRequest request,
                                                              HttpServletRequest httpRequest) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(httpRequest);
        if (botResponse != null) {
            return botResponse;
        }
        logger.debug("Async request no longer usable: {}", request.getDescription(false));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<String> handleInternalAuthentication(InternalAuthenticationServiceException ex,
                                                               WebRequest request,
                                                               HttpServletRequest httpRequest) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(httpRequest);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("InternalAuthenticationServiceException: {}", request.getDescription(false));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
    }

    /**
     * Final fallback: anything unclaimed is a server error, is logged with its
     * stack trace and is recorded as system feedback. A feedback-write failure
     * must never replace the original error (FIX: the catch below keeps the 500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnhandled(Exception ex,
                                                  WebRequest request,
                                                  HttpServletRequest httpRequest) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(httpRequest);
        if (botResponse != null) {
            return botResponse;
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

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
    }

    private ResponseEntity<String> routeMissingResource(HttpServletRequest request) {
        if (isApiRequest(request)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/error/404"))
                .build();
    }

    private boolean isApiRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/");
    }
}
