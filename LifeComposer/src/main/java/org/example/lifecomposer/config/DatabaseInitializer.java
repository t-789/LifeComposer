package org.example.lifecomposer.config;

import jakarta.annotation.PostConstruct;
import org.example.lifecomposer.Repository.FeedbackRepository;
import org.example.lifecomposer.Repository.GoalRepository;
import org.example.lifecomposer.Repository.ChatMessageRepository;
import org.example.lifecomposer.Repository.PlanningHistoryRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserProfileRepository userProfileRepository;
    private final GoalRepository goalRepository;
    private final PlanningHistoryRepository planningHistoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(UserRepository userRepository,
                               FeedbackRepository feedbackRepository,
                               ChatMessageRepository chatMessageRepository,
                               UserProfileRepository userProfileRepository,
                               GoalRepository goalRepository,
                               PlanningHistoryRepository planningHistoryRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userProfileRepository = userProfileRepository;
        this.goalRepository = goalRepository;
        this.planningHistoryRepository = planningHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        userRepository.createUserTableIfNeeded();
        userRepository.migrateUserSchema();
        feedbackRepository.createFeedbackTableIfNeeded();
        userProfileRepository.createTableIfNeeded();
        goalRepository.createTableIfNeeded();
        planningHistoryRepository.createTableIfNeeded();
        chatMessageRepository.createChatMessageTableIfNeeded();

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
