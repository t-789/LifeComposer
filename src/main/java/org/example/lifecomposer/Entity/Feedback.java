package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class Feedback {
    private Integer id;
    private Integer userId;
    private String username;
    private String content;
    /** system | user */
    private String type;
    private String url;
    private String userAgent;
    private String stackTrace;
    private Timestamp createTime;
    private Boolean resolved;
    private String resolvedBy;
    private Timestamp resolvedTime;
}
