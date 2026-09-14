package org.example.lifecomposer.Repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 7 defence-in-depth: the ORDER BY clause handed to SQLite is always a
 * plain identifier optionally followed by ASC/DESC, even though the service
 * already resolves it from a whitelist.
 */
class AdminQueryRepositoryTest {

    @Test
    @DisplayName("valid sort expressions pass through")
    void validSortExpressions() {
        assertThat(AdminQueryRepository.safeOrderBy("u.id DESC")).isEqualTo("u.id DESC");
        assertThat(AdminQueryRepository.safeOrderBy("m.create_time ASC")).isEqualTo("m.create_time ASC");
        assertThat(AdminQueryRepository.safeOrderBy("total")).isEqualTo("total");
    }

    @Test
    @DisplayName("blank input falls back to the primary key")
    void blankFallsBack() {
        assertThat(AdminQueryRepository.safeOrderBy(null)).isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("   ")).isEqualTo("id ASC");
    }

    @Test
    @DisplayName("SQL injection attempts fall back to the safe default")
    void injectionAttemptsRejected() {
        assertThat(AdminQueryRepository.safeOrderBy("id; DROP TABLE users")).isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("id) --")).isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("id UNION SELECT password_hash FROM users"))
                .isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("(SELECT 1)")).isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("id DESC, password_hash")).isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("id/**/DESC")).isEqualTo("id ASC");
        assertThat(AdminQueryRepository.safeOrderBy("password_hash")).isEqualTo("password_hash");
    }
}
