package org.example.lifecomposer.Security;

import org.example.lifecomposer.config.AppSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 7: the optional intranet-only restriction also covers the new debug
 * console (pages and API), without touching public paths or console assets.
 */
class AdminIntranetGuardFilterTest {

    private static final String PUBLIC_IP = "203.0.113.7";

    @Test
    @DisplayName("app.security.admin-intranet-only is off by default")
    void disabledByDefault() throws Exception {
        AdminIntranetGuardFilter filter = new AdminIntranetGuardFilter(new AppSecurityProperties());

        MockHttpServletResponse response = run(filter, "/api/admin/users", PUBLIC_IP);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("an external client is blocked from the console API and pages")
    void externalClientBlockedFromConsole() throws Exception {
        AdminIntranetGuardFilter filter = enabledFilter();

        assertThat(run(filter, "/api/admin/users", PUBLIC_IP).getStatus()).isEqualTo(403);
        assertThat(run(filter, "/api/admin/dashboard", PUBLIC_IP).getStatus()).isEqualTo(403);
        assertThat(run(filter, "/admin", PUBLIC_IP).getStatus()).isEqualTo(403);
        assertThat(run(filter, "/admin/user", PUBLIC_IP).getStatus()).isEqualTo(403);
        assertThat(run(filter, "/api/users/all", PUBLIC_IP).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("loopback and private clients keep access")
    void privateClientsAllowed() throws Exception {
        AdminIntranetGuardFilter filter = enabledFilter();

        assertThat(run(filter, "/api/admin/users", "127.0.0.1").getStatus()).isEqualTo(200);
        assertThat(run(filter, "/admin", "192.168.1.20").getStatus()).isEqualTo(200);
        assertThat(run(filter, "/admin", "10.1.2.3").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("public endpoints and console assets are never blocked")
    void publicPathsAndAssetsUnaffected() throws Exception {
        AdminIntranetGuardFilter filter = enabledFilter();

        assertThat(run(filter, "/api/qa/health", PUBLIC_IP).getStatus()).isEqualTo(200);
        assertThat(run(filter, "/api/users/login", PUBLIC_IP).getStatus()).isEqualTo(200);
        assertThat(run(filter, "/admin.js", PUBLIC_IP).getStatus()).isEqualTo(200);
        assertThat(run(filter, "/admin.css", PUBLIC_IP).getStatus()).isEqualTo(200);
        assertThat(run(filter, "/csrf.js", PUBLIC_IP).getStatus()).isEqualTo(200);
    }

    private AdminIntranetGuardFilter enabledFilter() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setAdminIntranetOnly(true);
        return new AdminIntranetGuardFilter(properties);
    }

    private MockHttpServletResponse run(AdminIntranetGuardFilter filter, String uri, String remoteAddr)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        if (chainInvoked.get()) {
            response.setStatus(200);
        }
        return response;
    }
}
