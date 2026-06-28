package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public CustomUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        User user = userService.getByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        boolean disabled = Boolean.TRUE.equals(user.getBanned())
                && (user.getBanEndTime() == null || user.getBanEndTime().toInstant().isAfter(Instant.now()));

        String role = user.getType() != null && user.getType() == UserType.ADMIN ? "ADMIN" : "USER";

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .roles(role)
                .disabled(disabled)
                .build();
    }
}
