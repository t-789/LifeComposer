package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class ChatMessage {
    private Integer id;
    private Integer userId;
    /**
     * "user" / "assistant" / "tool". Assistant rows may carry a structured
     * tool_call payload; tool rows carry the matching tool_result payload.
     */
    private String role;
    private String content;
    private Timestamp createTime;
}
