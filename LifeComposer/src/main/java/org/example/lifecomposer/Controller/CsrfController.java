package org.example.lifecomposer.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.lifecomposer.Security.XsrfHeaderAliasFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exposes the per-session CSRF token for same-origin browser clients. */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public Map<String, Object> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headerName", XsrfHeaderAliasFilter.ALIAS_HEADER);
        body.put("parameterName", token != null ? token.getParameterName() : "_csrf");
        body.put("token", token != null ? token.getToken() : null);
        return body;
    }
}
