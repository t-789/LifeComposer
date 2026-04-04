package org.example.lifecomposer.config;

import jakarta.annotation.PostConstruct;
import org.example.lifecomposer.Repository.FeedbackRepository;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.entity.User;
import org.example.lifecomposer.entity.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(UserRepository userRepository,
                               FeedbackRepository feedbackRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        userRepository.createUserTableIfNeeded();
        userRepository.migrateUserSchema();
        feedbackRepository.createFeedbackTableIfNeeded();

        createDefaultAdminIfMissing();
    }

    private void createDefaultAdminIfMissing() {
        if (userRepository.countAdminUsers() > 0) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setType(UserType.ADMIN);
        admin.setBanned(false);

        userRepository.insertUser(admin);
    }
}
