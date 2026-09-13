package org.example.lifecomposer.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Spring Security's CsrfFilter reads the default header {@code X-CSRF-TOKEN}.
 * Browser clients in this project send {@code X-XSRF-TOKEN}; this filter maps
 * the alias to the default header so the same token semantics apply.
 */
public class XsrfHeaderAliasFilter extends OncePerRequestFilter {

    public static final String ALIAS_HEADER = "X-XSRF-TOKEN";
    public static final String DEFAULT_HEADER = "X-CSRF-TOKEN";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String alias = request.getHeader(ALIAS_HEADER);
        if (alias == null || alias.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (DEFAULT_HEADER.equalsIgnoreCase(name)) {
                    return alias;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (DEFAULT_HEADER.equalsIgnoreCase(name)) {
                    return Collections.enumeration(Collections.singletonList(alias));
                }
                return super.getHeaders(name);
            }
        }, response);
    }
}
