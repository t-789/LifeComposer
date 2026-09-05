package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 标准能力标签字典（capability_tags）只读接口。 */
@RestController
@RequestMapping("/api/capability-tags")
public class CapabilityTagController {

    private final CapabilityTagRepository capabilityTagRepository;

    public CapabilityTagController(CapabilityTagRepository capabilityTagRepository) {
        this.capabilityTagRepository = capabilityTagRepository;
    }

    /** GET /api/capability-tags：返回全部标准能力标签（按标签名排序）。 */
    @GetMapping
    public ResponseEntity<List<CapabilityTag>> listCapabilityTags() {
        return ResponseEntity.ok(capabilityTagRepository.findAll());
    }
}
