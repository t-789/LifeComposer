package org.example.lifecomposer.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.lifecomposer.config.AppSecurityProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Optional defense-in-depth: when app.security.admin-intranet-only=true, admin
 * APIs accept only loopback / private-network clients. Default is disabled.
 */
@Component
public class AdminIntranetGuardFilter extends OncePerRequestFilter {

    private final AppSecurityProperties properties;

    public AdminIntranetGuardFilter(AppSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.isAdminIntranetOnly()
                && isAdminPath(request.getRequestURI())
                && !isPrivateAddress(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Admin API is restricted to the intranet");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAdminPath(String uri) {
        return uri.startsWith("/api/users/admin/")
                || uri.equals("/api/users/all")
                || uri.startsWith("/api/feedback/all")
                || uri.startsWith("/api/feedback/type/")
                || (uri.startsWith("/api/feedback/") && uri.endsWith("/resolve"))
                // Milestone 7: the whole debug console follows the same optional
                // intranet restriction as the legacy admin endpoints.
                || uri.startsWith("/api/admin/")
                || uri.equals("/admin")
                || uri.startsWith("/admin/");
    }

    private boolean isPrivateAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            return inetAddress.isLoopbackAddress()
                    || inetAddress.isSiteLocalAddress()
                    || inetAddress.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
