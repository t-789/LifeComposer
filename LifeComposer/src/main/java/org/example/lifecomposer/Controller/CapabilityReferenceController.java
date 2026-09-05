package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 能力映射/模板/大类字典（capability_reference）只读接口。 */
@RestController
@RequestMapping("/api/capability-reference")
public class CapabilityReferenceController {

    private static final Set<String> VALID_SECTIONS = Set.of(
            "tags_to_merge", "skill_mapping", "skill_profiles",
            "role_profiles", "major_categories", "_meta");

    private final CapabilityReferenceRepository capabilityReferenceRepository;

    public CapabilityReferenceController(CapabilityReferenceRepository capabilityReferenceRepository) {
        this.capabilityReferenceRepository = capabilityReferenceRepository;
    }

    /** GET /api/capability-reference?section=skill_mapping（不带 section 返回全部字典行）。 */
    @GetMapping
    public ResponseEntity<?> listCapabilityReferences(@RequestParam(required = false) String section) {
        if (section == null || section.isBlank()) {
            return ResponseEntity.ok(capabilityReferenceRepository.findAll());
        }
        if (!VALID_SECTIONS.contains(section)) {
            return ResponseEntity.badRequest().body("section 参数不合法，仅支持: " + VALID_SECTIONS);
        }
        return ResponseEntity.ok(capabilityReferenceRepository.findBySection(section));
    }
}
