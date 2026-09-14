package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.AdminSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPasswordPolicyTest {

    private AdminPasswordPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AdminPasswordPolicy(new AdminSecurityProperties());
    }

    @Test
    @DisplayName("strong password is accepted")
    void strongPasswordAccepted() {
        assertThat(policy.violation("Str0ng-Admin-Pass!")).isEmpty();
    }

    @Test
    @DisplayName("null / blank passwords are rejected")
    void nullOrBlankRejected() {
        assertThat(policy.violation(null)).isPresent();
        assertThat(policy.violation("   ")).isPresent();
    }

    @Test
    @DisplayName("passwords shorter than the minimum length are rejected")
    void tooShortRejected() {
        assertThat(policy.violation("Short1!")).isPresent();
        // exactly min length is accepted
        assertThat(policy.violation("Abcdefghij1!")).isEmpty();
    }

    @Test
    @DisplayName("passwords longer than the BCrypt limit are rejected")
    void tooLongRejected() {
        assertThat(policy.violation("A1!" + "x".repeat(80))).isPresent();
    }

    @Test
    @DisplayName("known weak passwords are rejected case-insensitively")
    void knownWeakPasswordsRejected() {
        assertThat(policy.violation("admin")).isPresent();
        assertThat(policy.violation("ADMIN")).isPresent();
        assertThat(policy.violation("testuser")).isPresent();
        assertThat(policy.violation("LifeComposer")).isPresent();
        assertThat(policy.violation("000000000000")).isPresent();
        assertThat(policy.violation("aaaaaaaaaaaa")).isPresent();
    }

    @Test
    @DisplayName("violation messages never echo the submitted password")
    void violationDoesNotEchoPassword() {
        String secret = "sup3rSecretValue!";
        Optional<String> violation = policy.violation(secret + " ");
        assertThat(violation).isPresent();
        assertThat(violation.get()).doesNotContain(secret);
    }

    @Test
    @DisplayName("requireStrong throws an IllegalStateException naming the env variable only")
    void requireStrongNamesEnvVariable() {
        assertThatCode(() -> policy.requireStrong("Str0ng-Admin-Pass!", "初始化"))
                .doesNotThrowAnyException();

        AdminSecurityProperties custom = new AdminSecurityProperties();
        custom.setWeakPasswords(java.util.List.of("my-weak-secret-1"));
        AdminPasswordPolicy customPolicy = new AdminPasswordPolicy(custom);

        assertThatThrownBy(() -> customPolicy.requireStrong("my-weak-secret-1", "初始化"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdminSecurityProperties.INITIAL_PASSWORD_ENV)
                .hasMessageNotContaining("my-weak-secret-1");
    }
}
