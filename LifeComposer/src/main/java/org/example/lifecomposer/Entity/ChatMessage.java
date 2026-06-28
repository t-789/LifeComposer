package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class ChatMessage {
    private Integer id;
    private Integer userId;
    private String role;   // "user" or "assistant"
    private String content;
    private Timestamp createTime;
}
