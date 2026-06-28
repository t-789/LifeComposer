package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatResponse {
    private String role;       // always "assistant"
    private String content;
    private String createTime; // formatted timestamp string
    private Boolean mocked;
    private String error;
}
