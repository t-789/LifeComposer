package org.example.lifecomposer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Milestone 6 administrator bootstrap / password strength configuration.
 *
 * <p>The initial admin password is bound from the environment variable
 * {@code LIFECOMPOSER_INITIAL_ADMIN_PASSWORD} (see application.properties). The
 * value is only ever read here and immediately handed to BCrypt; it is never
 * logged, echoed in an error message or written to a report.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "lifecomposer.admin")
@Getter
@Setter
public class AdminSecurityProperties {

    /** Name of the environment variable that carries the initial admin password. */
    public static final String INITIAL_PASSWORD_ENV = "LIFECOMPOSER_INITIAL_ADMIN_PASSWORD";

    /** Fixed bootstrap account name (first version keeps the historical name). */
    public static final String BOOTSTRAP_USERNAME = "admin";

    /** Password used by the removed legacy bootstrap; only used for detection. */
    public static final String LEGACY_DEFAULT_PASSWORD = "admin";

    /** Bound from ${LIFECOMPOSER_INITIAL_ADMIN_PASSWORD:} — empty means "not provided". */
    private String initialPassword = "";

    /** Minimum accepted length for any administrator password. */
    private int minPasswordLength = 12;

    /**
     * Minimum accepted length when a non-administrator replaces an
     * administrator-issued temporary password. Lower than the administrator rule
     * on purpose (existing user accounts may have short passwords), but the weak
     * list and the other checks still apply.
     */
    private int userMinPasswordLength = 8;

    /** BCrypt silently truncates beyond 72 bytes, so longer input is rejected. */
    private int maxPasswordLength = 72;

    /**
     * How long an administrator-issued temporary password stays usable. After
     * that the account cannot log in until a new temporary password is issued.
     */
    private int tempPasswordTtlMinutes = 24 * 60;

    /**
     * Known weak passwords. Matching is case-insensitive and never reflects the
     * submitted value back to the caller.
     */
    private List<String> weakPasswords = new ArrayList<>(List.of(
            "admin", "000000", "111111", "123456", "12345678", "password",
            "passw0rd", "testuser", "admin123", "administrator", "lifecomposer",
            "qwerty", "abc123", "root"
    ));

    /** Admin console: max accepted page size for every /api/admin/** query. */
    private int consoleMaxPageSize = 200;

    /** Admin console: default page size when the request omits pageSize. */
    private int consoleDefaultPageSize = 50;

    /** Admin console: read queries allowed per administrator per minute. */
    private int consoleQueriesPerMinute = 240;

    /** Admin console: JDBC query timeout in seconds for console read queries. */
    private int consoleQueryTimeoutSeconds = 10;

    /** Admin console: max characters of diagnostic text (stack traces, long stored text). */
    private int consoleDiagnosticLength = 4000;

    /** Admin console: max characters returned for long text columns in list views. */
    private int consolePreviewLength = 300;
}
