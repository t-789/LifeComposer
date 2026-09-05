package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CapabilityReference {
    private Long id;
    /** tags_to_merge / skill_mapping / skill_profiles / role_profiles / major_categories / _meta */
    private String section;
    private String refKey;
    private String refValue;
    private String note;
}
