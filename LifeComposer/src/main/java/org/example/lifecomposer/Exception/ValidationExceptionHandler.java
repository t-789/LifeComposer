package org.example.lifecomposer.Exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Boundary for client input errors: Bean Validation, malformed or missing JSON
 * bodies, type conversion, missing parameters and unsupported media types.
 *
 * <p>These branches used to live inside {@link GlobalExceptionHandler}'s single
 * {@code Exception.class} handler, which meant one handler carried unrelated
 * HTTP semantics (FIX: one exception type now has exactly one owner). This
 * advice is ordered {@link Ordered#HIGHEST_PRECEDENCE} so an exact match always
 * wins over the global catch-all, even when both could handle it.
 *
 * <p>Because this advice runs first, it also applies the shared
 * {@link BotRequestGuard} policy before answering: otherwise a bot request that
 * triggers a malformed body or a missing parameter would get a 400 and bypass
 * the documented "empty User-Agent / crawler keyword → 403" rule.
 *
 * <p>Response bodies intentionally keep their previous shapes: Bean Validation
 * returns a field-to-message map, the remaining client errors keep their plain
 * text contract.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ValidationExceptionHandler {

    private static final Logger logger = LogManager.getLogger(ValidationExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex,
                                                       HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("Constraint violation: {}", request.getRequestURI());
        return ResponseEntity.badRequest().body("Invalid input");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("Method argument type mismatch: {}", request.getRequestURI());
        return ResponseEntity.badRequest().body("Method argument type mismatch");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException ex,
                                                    HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("Missing servlet request parameter: {}", request.getRequestURI());
        return ResponseEntity.badRequest().body("Missing servlet request parameter.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                  HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("Malformed request body: {}", request.getRequestURI());
        // Plain text keeps this handler's uniform body contract; the frontend
        // surfaces the message as-is and never injects it as markup.
        return ResponseEntity.badRequest().body("请求体缺失或格式不正确");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                        HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        logger.warn("Media type not supported: {}", request.getRequestURI());
        return ResponseEntity.badRequest().body("Unsupported media type");
    }
}
