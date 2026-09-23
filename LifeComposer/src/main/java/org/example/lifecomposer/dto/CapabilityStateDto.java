package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CapabilityStateDto {
    private String tag;
    private String level;
    private List<String> evidence;
    private String source;
    private Double confidence;
    private String updatedAt;
}
