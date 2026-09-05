package org.example.lifecomposer.Controller;

import jakarta.validation.Valid;
import org.example.lifecomposer.Service.CollegeCreditRuleService;
import org.example.lifecomposer.dto.CollegeCreditRuleDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/college-credit-rules")
public class CollegeCreditRuleController {

    private final CollegeCreditRuleService collegeCreditRuleService;

    public CollegeCreditRuleController(CollegeCreditRuleService collegeCreditRuleService) {
        this.collegeCreditRuleService = collegeCreditRuleService;
    }

    @GetMapping
    public ResponseEntity<?> listRules(@RequestParam(required = false) String college,
                                       @RequestParam(required = false) String creditType,
                                       @RequestParam(required = false) String category) {
        try {
            return ResponseEntity.ok(collegeCreditRuleService.listRules(college, creditType, category));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRule(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(collegeCreditRuleService.getRule(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createRule(Authentication authentication,
                                        @Valid @RequestBody CollegeCreditRuleDto dto) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("需要管理员权限");
        }
        try {
            return ResponseEntity.ok(collegeCreditRuleService.createRule(dto));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
