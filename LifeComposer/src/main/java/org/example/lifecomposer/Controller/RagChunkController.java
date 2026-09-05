package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Repository.RagChunkRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 检索切片只读接口。related_resource_id 为可选过滤条件，
 * 逻辑关联 resources.resource_id 业务键（不关联资源的切片为 NULL）。
 */
@RestController
@RequestMapping("/api/rag-chunks")
public class RagChunkController {

    private final RagChunkRepository ragChunkRepository;

    public RagChunkController(RagChunkRepository ragChunkRepository) {
        this.ragChunkRepository = ragChunkRepository;
    }

    /** GET /api/rag-chunks?relatedResourceId=competition_001（不带参数返回全部切片）。 */
    @GetMapping
    public ResponseEntity<?> listRagChunks(@RequestParam(required = false) String relatedResourceId) {
        if (relatedResourceId == null || relatedResourceId.isBlank()) {
            return ResponseEntity.ok(ragChunkRepository.findAll());
        }
        return ResponseEntity.ok(ragChunkRepository.findByRelatedResourceId(relatedResourceId));
    }
}
