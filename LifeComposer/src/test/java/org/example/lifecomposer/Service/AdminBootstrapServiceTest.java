package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.config.AdminSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapServiceTest {

    private static final String STRONG = "Str0ng-Admin-Pass!2026";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AdminSecurityProperties properties;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        properties = new AdminSecurityProperties();
        service = new AdminBootstrapService(userRepository, passwordEncoder,
                new AdminPasswordPolicy(properties), properties);
    }

    @Test
    @DisplayName("empty database without the env password fails fast naming the variable")
    void missingPasswordFailsFast() {
        when(userRepository.countAdminUsers()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(null);

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdminSecurityProperties.INITIAL_PASSWORD_ENV);

        verify(userRepository, never()).insertUser(any());
    }

    @Test
    @DisplayName("empty database with a weak password fails fast and inserts nothing")
    void weakPasswordFailsFast() {
        when(userRepository.countAdminUsers()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(null);
        properties.setInitialPassword("testuser");

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdminSecurityProperties.INITIAL_PASSWORD_ENV)
                .hasMessageNotContaining("testuser");

        verify(userRepository, never()).insertUser(any());
    }

    @Test
    @DisplayName("empty database with a valid password creates exactly one BCrypt admin")
    void validPasswordCreatesAdmin() {
        when(userRepository.countAdminUsers()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(null);
        properties.setInitialPassword(STRONG);

        service.bootstrap();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insertUser(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("admin");
        assertThat(created.getType()).isEqualTo(UserType.ADMIN);
        assertThat(created.getPasswordHash()).isNotEqualTo(STRONG).startsWith("$2");
        assertThat(passwordEncoder.matches(STRONG, created.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("existing administrators need no env password and are never overwritten")
    void existingAdminIsNotOverwritten() {
        when(userRepository.countAdminUsers()).thenReturn(1L);
        User admin = adminWithPassword("Existing-Admin-Pass!2026");
        when(userRepository.findByUsername("admin")).thenReturn(admin);

        service.bootstrap();

        verify(userRepository, never()).insertUser(any());
        verify(userRepository, never()).updateUserPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("legacy admin/admin without a new password refuses to start")
    void legacyDefaultAdminRefusesStartup() {
        when(userRepository.countAdminUsers()).thenReturn(1L);
        when(userRepository.findByUsername("admin")).thenReturn(adminWithPassword("admin"));

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdminSecurityProperties.INITIAL_PASSWORD_ENV);

        verify(userRepository, never()).updateUserPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("legacy admin/admin is rotated when a valid password is provided")
    void legacyDefaultAdminIsRotated() {
        when(userRepository.countAdminUsers()).thenReturn(1L);
        when(userRepository.findByUsername("admin")).thenReturn(adminWithPassword("admin"));
        properties.setInitialPassword(STRONG);

        service.bootstrap();

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updateUserPassword(org.mockito.ArgumentMatchers.eq(1), hash.capture());
        assertThat(hash.getValue()).startsWith("$2");
        assertThat(passwordEncoder.matches(STRONG, hash.getValue())).isTrue();
        assertThat(passwordEncoder.matches("admin", hash.getValue())).isFalse();
    }

    @Test
    @DisplayName("legacy rotation refuses a weak provided password")
    void legacyRotationRefusesWeakPassword() {
        when(userRepository.countAdminUsers()).thenReturn(1L);
        when(userRepository.findByUsername("admin")).thenReturn(adminWithPassword("admin"));
        properties.setInitialPassword("000000");

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdminSecurityProperties.INITIAL_PASSWORD_ENV);

        verify(userRepository, never()).updateUserPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("a non-admin account named admin blocks bootstrap instead of leaking a password")
    void conflictingPlainUserNamedAdminFailsFast() {
        when(userRepository.countAdminUsers()).thenReturn(0L);
        User plain = new User();
        plain.setId(7);
        plain.setUsername("admin");
        plain.setType(UserType.USER);
        when(userRepository.findByUsername("admin")).thenReturn(plain);
        properties.setInitialPassword(STRONG);

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin");

        verify(userRepository, never()).insertUser(any());
    }

    private User adminWithPassword(String rawPassword) {
        User admin = new User();
        admin.setId(1);
        admin.setUsername("admin");
        admin.setType(UserType.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        return admin;
    }
}
