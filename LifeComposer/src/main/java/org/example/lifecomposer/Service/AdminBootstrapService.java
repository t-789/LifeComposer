package org.example.lifecomposer.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.config.AdminSecurityProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Milestone 6 administrator bootstrap.
 *
 * <p>Replaces the historical, fixed {@code admin/admin} account creation:</p>
 * <ol>
 *   <li>No administrator exists → the initial password must come from
 *       {@code LIFECOMPOSER_INITIAL_ADMIN_PASSWORD}; otherwise startup fails
 *       fast, naming only the variable.</li>
 *   <li>Administrators already exist → the variable is ignored and no existing
 *       password is ever overwritten.</li>
 *   <li>The identifiable legacy default account ({@code admin} whose BCrypt hash
 *       still matches {@code admin}) is force-rotated to the supplied password;
 *       without a valid password startup is refused.</li>
 * </ol>
 *
 * <p>Only the account name and the audit event are logged — never a password,
 * hash or variable value.</p>
 */
@Service
public class AdminBootstrapService {

    private static final Logger LOG = LogManager.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminPasswordPolicy passwordPolicy;
    private final AdminSecurityProperties properties;

    public AdminBootstrapService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AdminPasswordPolicy passwordPolicy,
                                 AdminSecurityProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
    }

    /** Idempotent bootstrap entry point, called once during startup. */
    public void bootstrap() {
        if (userRepository.countAdminUsers() > 0) {
            rotateLegacyDefaultAdminIfNeeded();
            return;
        }
        createInitialAdmin();
    }

    private void createInitialAdmin() {
        User conflicting = userRepository.findByUsername(AdminSecurityProperties.BOOTSTRAP_USERNAME);
        if (conflicting != null) {
            throw new IllegalStateException(
                    "数据库中存在名为 " + AdminSecurityProperties.BOOTSTRAP_USERNAME
                            + " 的普通账户，无法用它创建初始管理员，请先处理该账户后重新启动");
        }

        String password = properties.getInitialPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(missingPasswordMessage(
                    "数据库中没有管理员账户，需要设置初始管理员密码"));
        }
        passwordPolicy.requireStrong(password, "初始管理员密码不符合要求");

        User admin = new User();
        admin.setUsername(AdminSecurityProperties.BOOTSTRAP_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setType(UserType.ADMIN);
        admin.setBanned(false);
        userRepository.insertUser(admin);

        // Audit only the account name; the password and its hash stay out of logs.
        LOG.info("AUDIT event=admin_bootstrap_created username={}",
                AdminSecurityProperties.BOOTSTRAP_USERNAME);
    }

    private void rotateLegacyDefaultAdminIfNeeded() {
        User legacy = userRepository.findByUsername(AdminSecurityProperties.BOOTSTRAP_USERNAME);
        if (legacy == null
                || legacy.getType() == null
                || legacy.getType() != UserType.ADMIN
                || legacy.getPasswordHash() == null) {
            return;
        }
        if (!passwordEncoder.matches(AdminSecurityProperties.LEGACY_DEFAULT_PASSWORD,
                legacy.getPasswordHash())) {
            return;
        }

        String password = properties.getInitialPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(missingPasswordMessage(
                    "检测到管理员 " + AdminSecurityProperties.BOOTSTRAP_USERNAME
                            + " 仍在使用已废弃的默认弱密码，必须提供新密码完成强制轮换"));
        }
        passwordPolicy.requireStrong(password, "管理员密码轮换失败");

        userRepository.updateUserPassword(legacy.getId(), passwordEncoder.encode(password));
        LOG.info("AUDIT event=admin_password_rotated username={} reason=legacy_default_password",
                AdminSecurityProperties.BOOTSTRAP_USERNAME);
    }

    private String missingPasswordMessage(String prefix) {
        return prefix + "：请设置环境变量 "
                + AdminSecurityProperties.INITIAL_PASSWORD_ENV
                + "（至少 " + passwordPolicy.minPasswordLength()
                + " 个字符，不得使用 admin/000000/testuser 等已知弱密码）后重新启动";
    }
}
