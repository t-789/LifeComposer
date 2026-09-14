package org.example.lifecomposer.config;

import org.example.lifecomposer.LifeComposerApplication;
import org.example.lifecomposer.config.AdminSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milestone 6 acceptance at the real startup boundary: a web application with no
 * administrator and no {@code LIFECOMPOSER_INITIAL_ADMIN_PASSWORD} must refuse to
 * start (naming only the variable), and a legacy {@code admin/admin} database must
 * be force-rotated.
 *
 * <p>Each case boots a throwaway Spring context on its own SQLite file so the
 * shared test database is never touched.</p>
 */
class AdminBootstrapStartupTest {

    private static final String STRONG_PASSWORD = "Startup-Test-Pass!2026";
    private static final String ROTATED_PASSWORD = "Rotated-Test-Pass!2026";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("empty database + no initial password => startup fails naming the variable")
    void emptyDatabaseWithoutPasswordFailsFast() {
        String jdbcUrl = jdbcUrl("no-password.db");

        assertThatThrownBy(() -> start(jdbcUrl, ""))
                .satisfies(error -> assertThat(causeMessages(error))
                        .contains(AdminSecurityProperties.INITIAL_PASSWORD_ENV));
    }

    @Test
    @DisplayName("empty database + weak initial password => startup fails without echoing it")
    void emptyDatabaseWithWeakPasswordFailsFast() {
        String jdbcUrl = jdbcUrl("weak-password.db");

        assertThatThrownBy(() -> start(jdbcUrl, "testuser"))
                .satisfies(error -> {
                    String messages = causeMessages(error);
                    assertThat(messages).contains(AdminSecurityProperties.INITIAL_PASSWORD_ENV);
                    assertThat(messages).doesNotContain("testuser");
                });
    }

    @Test
    @DisplayName("empty database + valid password => single BCrypt admin; later starts ignore the variable")
    void validPasswordCreatesAdminAndIsNeverOverwritten() throws Exception {
        String jdbcUrl = jdbcUrl("valid-password.db");

        try (ConfigurableApplicationContext context = start(jdbcUrl, STRONG_PASSWORD)) {
            assertThat(adminCount(context)).isEqualTo(1);
            assertThat(adminHash(context)).startsWith("$2");
            assertThat(matches(STRONG_PASSWORD, adminHash(context))).isTrue();
        }

        // Second start without the variable: administrators already exist.
        try (ConfigurableApplicationContext context = start(jdbcUrl, "")) {
            assertThat(adminCount(context)).isEqualTo(1);
            assertThat(matches(STRONG_PASSWORD, adminHash(context))).isTrue();
        }

        // Third start with a different variable value: the existing password stands.
        try (ConfigurableApplicationContext context = start(jdbcUrl, "Another-Passw0rd!2026")) {
            assertThat(adminCount(context)).isEqualTo(1);
            assertThat(matches(STRONG_PASSWORD, adminHash(context))).isTrue();
        }
    }

    @Test
    @DisplayName("legacy admin/admin database requires and applies a rotation")
    void legacyDefaultAdminIsRotated() throws Exception {
        String jdbcUrl = jdbcUrl("legacy.db");

        try (ConfigurableApplicationContext context = start(jdbcUrl, STRONG_PASSWORD)) {
            assertThat(adminCount(context)).isEqualTo(1);
        }

        // Simulate a database created by the removed v0.0.5 bootstrap.
        writeLegacyDefaultHash(jdbcUrl);

        assertThatThrownBy(() -> start(jdbcUrl, ""))
                .satisfies(error -> assertThat(causeMessages(error))
                        .contains(AdminSecurityProperties.INITIAL_PASSWORD_ENV));

        try (ConfigurableApplicationContext context = start(jdbcUrl, ROTATED_PASSWORD)) {
            assertThat(matches(ROTATED_PASSWORD, adminHash(context))).isTrue();
            assertThat(matches("admin", adminHash(context))).isFalse();
        }

        // Once rotated, the variable is no longer needed.
        try (ConfigurableApplicationContext context = start(jdbcUrl, "")) {
            assertThat(matches(ROTATED_PASSWORD, adminHash(context))).isTrue();
        }
    }

    // ------------------------------------------------------------------ helpers

    private String jdbcUrl(String fileName) {
        return "jdbc:sqlite:" + tempDir.resolve(fileName).toAbsolutePath();
    }

    private ConfigurableApplicationContext start(String jdbcUrl, String initialPassword) {
        SpringApplication application = new SpringApplication(LifeComposerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        return application.run(
                "--spring.datasource.url=" + jdbcUrl,
                "--lifecomposer.admin.initial-password=" + initialPassword,
                "--embedding.enabled=false",
                "--spring.devtools.restart.enabled=false",
                "--logging.level.root=WARN",
                "--logging.level.org.springframework=WARN",
                "--logging.level.org.hibernate=WARN");
    }

    private int adminCount(ConfigurableApplicationContext context) throws Exception {
        return queryInt(context, "SELECT COUNT(*) FROM users WHERE type = 2");
    }

    private String adminHash(ConfigurableApplicationContext context) throws Exception {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT password_hash FROM users WHERE username = 'admin'")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private int queryInt(ConfigurableApplicationContext context, String sql) throws Exception {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private void writeLegacyDefaultHash(String jdbcUrl) throws Exception {
        String legacyHash = new BCryptPasswordEncoder().encode("admin");
        try (Connection connection = java.sql.DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE users SET password_hash = '" + legacyHash
                    + "' WHERE username = 'admin'");
        }
    }

    private boolean matches(String rawPassword, String hash) {
        return new BCryptPasswordEncoder().matches(rawPassword, hash);
    }

    private String causeMessages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return messages.toString();
    }
}
