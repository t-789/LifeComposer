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
    /** JSON 数组形式的向量；NULL 表示尚未生成。 */
    private String embeddingJson;
    private String embeddingModel;
    private Integer embeddingDimensions;
    /** PENDING / SUCCESS / FAILED / SKIPPED */
    private String embeddingStatus;
    private String embeddingError;
    private String embeddingUpdatedAt;
    /** SHA-256(text) 用于判断是否需要重新生成 embedding。 */
    private String contentHash;
}
