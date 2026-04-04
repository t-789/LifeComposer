package org.example.lifecomposer.service;

import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.entity.User;
import org.example.lifecomposer.entity.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean register(String username, String rawPassword) {
        if (userRepository.findByUsername(username) != null) {
            return false;
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setType(UserType.USER);
        user.setBanned(false);

        return userRepository.insertUser(user) > 0;
    }

    public User login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return null;
        }

        if (Boolean.TRUE.equals(user.getBanned())) {
            Timestamp banEndTime = user.getBanEndTime();
            if (banEndTime == null || banEndTime.after(Timestamp.from(Instant.now()))) {
                throw new IllegalStateException("账户被封禁至" + (banEndTime == null ? "永久" : banEndTime));
            }
            // Ban window is over, auto-unban for consistency.
            userRepository.updateBanStatus(user.getId(), false, null);
            user.setBanned(false);
            user.setBanEndTime(null);
        }

        return passwordEncoder.matches(rawPassword, user.getPasswordHash()) ? user : null;
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User getById(int id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public boolean grantAdminPermission(int userId) {
        return userRepository.updateUserType(userId, UserType.ADMIN);
    }

    public boolean revokeAdminPermission(int userId) {
        return userRepository.updateUserType(userId, UserType.USER);
    }

    public boolean banUser(int userId, String banTimeExpr) {
        if ("0".equals(banTimeExpr)) {
            return userRepository.updateBanStatus(userId, true, null);
        }
        long ms = parseBanTimeToMillis(banTimeExpr);
        if (ms <= 0) {
            return false;
        }
        return userRepository.updateBanStatus(userId, true, new Timestamp(System.currentTimeMillis() + ms));
    }

    public boolean unbanUser(int userId) {
        return userRepository.updateBanStatus(userId, false, null);
    }

    public boolean resetPassword(int userId, String newPassword) {
        return userRepository.updateUserPassword(userId, passwordEncoder.encode(newPassword));
    }

    private long parseBanTimeToMillis(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return -1;
        }

        long totalMillis = 0;
        int i = 0;
        while (i < timeStr.length()) {
            int start = i;
            while (i < timeStr.length() && Character.isDigit(timeStr.charAt(i))) {
                i++;
            }
            if (start == i || i >= timeStr.length()) {
                return -1;
            }

            int number = Integer.parseInt(timeStr.substring(start, i));
            char unit = timeStr.charAt(i++);

            switch (unit) {
                case 'y' -> totalMillis += Duration.ofDays(365L * number).toMillis();
                case 'm' -> totalMillis += Duration.ofDays(30L * number).toMillis();
                case 'd' -> totalMillis += Duration.ofDays(number).toMillis();
                case 'h' -> totalMillis += Duration.ofHours(number).toMillis();
                default -> {
                    return -1;
                }
            }
        }

        return totalMillis;
    }
}
