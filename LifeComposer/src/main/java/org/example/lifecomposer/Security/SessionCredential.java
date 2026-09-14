package org.example.lifecomposer.Security;

import jakarta.servlet.http.HttpSession;
import org.example.lifecomposer.Entity.User;

/**
 * Session-side state for the credential generation of the logged-in user.
 *
 * <p>Rationale (review follow-up): the application is session-cookie based with
 * no server-side session registry, so "invalidate the old sessions after a
 * password reset" is implemented by stamping every login with the user's
 * {@code credential_version} and clearing the stamp on the session that performs
 * the change. {@link SessionCredentialGuardFilter} compares the stamp with the
 * database on every request.</p>
 */
public final class SessionCredential {

    /** Credential generation the session authenticated with. */
    public static final String ATTR_VERSION = "lifecomposer.credentialVersion";

    /** True while the session may only be used to replace a temporary password. */
    public static final String ATTR_RESET_REQUIRED = "lifecomposer.passwordResetRequired";

    /** Deadline of the temporary password, so an idle forced-change session dies too. */
    public static final String ATTR_TEMP_EXPIRES_AT = "lifecomposer.tempPasswordExpiresAtMillis";

    private SessionCredential() {
    }

    /** Stamps a freshly authenticated (or just refreshed) session. */
    public static void bind(HttpSession session, User user) {
        if (session == null || user == null) {
            return;
        }
        session.setAttribute(ATTR_VERSION,
                user.getCredentialVersion() == null ? 1 : user.getCredentialVersion());
        session.setAttribute(ATTR_RESET_REQUIRED, Boolean.TRUE.equals(user.getPasswordResetRequired()));
        session.setAttribute(ATTR_TEMP_EXPIRES_AT, user.getTempPasswordExpiresAt() == null
                ? null : user.getTempPasswordExpiresAt().getTime());
    }

    public static Integer version(HttpSession session) {
        Object value = session.getAttribute(ATTR_VERSION);
        return value instanceof Integer version ? version : null;
    }

    public static boolean resetRequired(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(ATTR_RESET_REQUIRED));
    }

    public static Long tempExpiresAtMillis(HttpSession session) {
        Object value = session.getAttribute(ATTR_TEMP_EXPIRES_AT);
        return value instanceof Long millis ? millis : null;
    }

    public static void clear(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(ATTR_VERSION);
        session.removeAttribute(ATTR_RESET_REQUIRED);
        session.removeAttribute(ATTR_TEMP_EXPIRES_AT);
    }
}
