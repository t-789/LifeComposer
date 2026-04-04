package org.example.lifecomposer.entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class User {
    private Integer id;
    private String username;
    private String passwordHash;
    /**
     * type=1 -> USER, type=2 -> ADMIN
     */
    private Integer type;
    private Boolean banned;
    private Timestamp banEndTime;
    private String avatar;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
