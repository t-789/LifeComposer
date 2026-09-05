package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RagChunk {
    /** 切片唯一 id，如 rag_001 */
    private String chunkId;
    private String title;
    private String text;
    private String sourceType;
    private String sourceUrl;
    private String sourceFile;
    private String pageOrSection;
    /** 逻辑关联 resources.resource_id（业务 id），可为 NULL */
    private String relatedResourceId;
    private String createdAt;
}
