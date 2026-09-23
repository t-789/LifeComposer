package org.example.lifecomposer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** v0.1 M2 configuration for the chat-driven profile change confirmation flow. */
@Configuration
@ConfigurationProperties(prefix = "lifecomposer.profile-change")
@Getter
@Setter
public class ProfileChangeProperties {

    /** Pending candidates expire after this many minutes. */
    private long ttlMinutes = 30;

    /**
     * Fields the chat agent may propose. Identity fields (studentId, college,
     * major, grade) are deliberately excluded: they must be filled in the
     * formal profile form, not inferred from a conversation.
     */
    private List<String> allowedFields = new ArrayList<>(List.of(
            "availableTime", "skillsJson", "interestsJson", "experiencesJson", "goals"));
}
