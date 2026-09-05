package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class CapabilityTag {
    /** 标准标签名（主键） */
    private String name;
    /** 技术能力 / 通用能力 */
    private String category;
    private String level1Desc;
    private String level2Desc;
    private String level3Desc;
    private String skillAliasesJson;
    private String typicalEvidenceJson;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
