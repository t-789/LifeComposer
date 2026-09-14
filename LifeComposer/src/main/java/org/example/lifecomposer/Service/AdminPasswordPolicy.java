package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.AdminSecurityProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Milestone 6 password strength rules for administrator credentials.
 *
 * <p>Applies to the initial bootstrap password, the forced rotation of a legacy
 * {@code admin/admin} account and administrator-triggered password resets.
 * Ordinary user registration keeps its own (weaker) rules so that this change
 * cannot silently break existing clients.</p>
 *
 * <p>Violation messages never contain the submitted password.</p>
 */
@Component
public class AdminPasswordPolicy {

    private final AdminSecurityProperties properties;

    public AdminPasswordPolicy(AdminSecurityProperties properties) {
        this.properties = properties;
    }

    /** @return a human readable violation message, or empty when the password is acceptable. */
    public Optional<String> violation(String rawPassword) {
        return violation(rawPassword, properties.getMinPasswordLength());
    }

    /**
     * Rule applied when a non-administrator replaces an administrator-issued
     * temporary password: same checks, lower length floor.
     */
    public Optional<String> violationForUser(String rawPassword) {
        return violation(rawPassword, Math.min(properties.getUserMinPasswordLength(),
                properties.getMinPasswordLength()));
    }

    private Optional<String> violation(String rawPassword, int minLength) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return Optional.of("密码不能为空");
        }
        if (rawPassword.length() < minLength) {
            return Optional.of("密码长度至少为 " + minLength + " 个字符");
        }
        if (rawPassword.length() > properties.getMaxPasswordLength()) {
            return Optional.of("密码长度不能超过 " + properties.getMaxPasswordLength() + " 个字符");
        }
        if (rawPassword.chars().anyMatch(Character::isWhitespace)) {
            return Optional.of("密码不能包含空白字符");
        }

        String normalized = rawPassword.toLowerCase(Locale.ROOT);
        if (properties.getWeakPasswords().stream()
                .map(seed -> seed.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals)) {
            return Optional.of("密码过于简单，请勿使用常见弱密码");
        }
        if (isSingleRepeatedCharacter(rawPassword)) {
            return Optional.of("密码不能由同一个字符重复组成");
        }
        return Optional.empty();
    }

    /**
     * Fail-fast helper for bootstrap paths: throws an {@link IllegalStateException}
     * that names the environment variable but never the offending value.
     */
    public void requireStrong(String rawPassword, String purpose) {
        Optional<String> violation = violation(rawPassword);
        if (violation.isPresent()) {
            throw new IllegalStateException(purpose + "：" + violation.get()
                    + "。请通过环境变量 "
                    + AdminSecurityProperties.INITIAL_PASSWORD_ENV
                    + " 提供符合要求的密码后重新启动");
        }
    }

    public int minPasswordLength() {
        return properties.getMinPasswordLength();
    }

    private boolean isSingleRepeatedCharacter(String value) {
        return value.chars().distinct().count() == 1L;
    }
}
