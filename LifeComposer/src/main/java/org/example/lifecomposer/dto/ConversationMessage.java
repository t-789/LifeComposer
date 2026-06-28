package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversationMessage {
    private String role;   // "user" or "assistant" or "system"
    private String content;
}
