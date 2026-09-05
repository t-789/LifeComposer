package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成长资源库（竞赛 + 课程）只读接口，供推荐/RAG 模块读取参考字典。
 * 数据侧业务键为 resource_id（如 competition_001 / course_001）。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceRepository resourceRepository;

    public ResourceController(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    /** GET /api/resources?type=competition|course（不带 type 返回全部）。 */
    @GetMapping
    public ResponseEntity<?> listResources(@RequestParam(required = false) String type) {
        if (type == null || type.isBlank()) {
            return ResponseEntity.ok(resourceRepository.findAll());
        }
        if (!"competition".equals(type) && !"course".equals(type)) {
            return ResponseEntity.badRequest().body("type 参数仅支持 competition 或 course");
        }
        List<Resource> resources = resourceRepository.findByType(type);
        return ResponseEntity.ok(resources);
    }

    /** GET /api/resources/{id}：优先按业务键 resource_id（如 competition_001）匹配，纯数字 id 回退到物理主键。 */
    @GetMapping("/{id}")
    public ResponseEntity<?> getResource(@PathVariable String id) {
        Resource resource = resourceRepository.findByResourceId(id);
        if (resource == null && id.matches("\\d+")) {
            resource = resourceRepository.findById(Long.parseLong(id));
        }
        if (resource == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("资源不存在: " + id);
        }
        return ResponseEntity.ok(resource);
    }
}
