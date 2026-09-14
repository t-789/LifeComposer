package org.example.lifecomposer.config;

import org.example.lifecomposer.LifeComposerApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review follow-up: upgrading an existing v0.0.5 database must add the temporary
 * password / credential-generation columns without losing data, so the new login
 * and session semantics work on legacy installs too.
 */
class UserSchemaMigrationTest {

    private static final String PASSWORD = "Migration-Test-Pass!2026";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("a legacy users table gains the v0.0.6 credential columns")
    void legacySchemaIsMigrated() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        String jdbcUrl = "jdbc:sqlite:" + database.toAbsolutePath();

        createLegacySchemaAndAdmin(jdbcUrl);

        try (ConfigurableApplicationContext context = start(jdbcUrl)) {
            List<String> columns = columns(context.getBean(DataSource.class));
            assertThat(columns).contains(
                    "password_reset_required", "temp_password_expires_at",
                    "password_changed_at", "credential_version");

            // Legacy rows receive safe defaults and the pre-existing admin survives.
            assertThat(queryLong(context, "SELECT COUNT(*) FROM users WHERE type = 2")).isEqualTo(1);
            assertThat(queryLong(context,
                    "SELECT password_reset_required FROM users WHERE username = 'admin'")).isZero();
            assertThat(queryLong(context,
                    "SELECT credential_version FROM users WHERE username = 'admin'")).isEqualTo(1);
        }
    }

    private void createLegacySchemaAndAdmin(String jdbcUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      username TEXT UNIQUE NOT NULL,
                      password_hash TEXT NOT NULL,
                      type INTEGER NOT NULL DEFAULT 1,
                      is_banned BOOLEAN NOT NULL DEFAULT 0,
                      ban_end_time TIMESTAMP NULL,
                      avatar TEXT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .encode(PASSWORD);
            statement.executeUpdate("INSERT INTO users(username, password_hash, type, is_banned) "
                    + "VALUES ('admin', '" + hash + "', 2, 0)");
        }
    }

    private ConfigurableApplicationContext start(String jdbcUrl) {
        SpringApplication application = new SpringApplication(LifeComposerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        return application.run(
                "--spring.datasource.url=" + jdbcUrl,
                "--lifecomposer.admin.initial-password=" + PASSWORD,
                "--embedding.enabled=false",
                "--spring.devtools.restart.enabled=false",
                "--logging.level.root=WARN");
    }

    private List<String> columns(DataSource dataSource) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(users)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private long queryLong(ConfigurableApplicationContext context, String sql) throws Exception {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }
}
