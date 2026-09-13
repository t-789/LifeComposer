package org.example.lifecomposer.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.CapabilityReference;
import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Entity.CollegeCreditRule;
import org.example.lifecomposer.Entity.RagChunk;
import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserProfile;
import org.example.lifecomposer.Entity.UserType;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.embedding.EmbeddingClient;
import org.example.lifecomposer.embedding.EmbeddingException;
import org.example.lifecomposer.embedding.EmbeddingResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Importers for the sample data package. Each record is validated and written
 * independently; one bad record or file never aborts the batch.
 */
@Service
public class DataImportService {

    private static final Logger LOG = LogManager.getLogger(DataImportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> RESOURCE_TYPES = Set.of("competition", "course");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> DATA_QUALITIES = Set.of("complete", "partial", "needs_review");
    private static final Set<String> SOURCE_TYPES = Set.of("web", "pdf", "json");
    private static final Set<String> CREDIT_TYPES = Set.of("graduation", "recommendation");
    private static final Set<String> CATEGORIES = Set.of("competition", "lecture", "course", "project",
            "paper", "patent", "sports", "arts", "veteran");
    private static final Set<String> COMP_LEVELS = Set.of("S", "A+", "A", "B+", "B", "national", "provincial", "school");
    private static final Set<String> AWARD_TIERS = Set.of("first", "second", "third", "special", "participation");
    private static final Set<String> TAG_CATEGORIES = Set.of("技术能力", "通用能力");
    private static final List<String> REFERENCE_SECTIONS = List.of(
            "tags_to_merge", "skill_mapping", "skill_profiles", "role_profiles", "major_categories");

    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RagChunkRepository ragChunkRepository;
    private final CapabilityTagRepository capabilityTagRepository;
    private final CapabilityReferenceRepository capabilityReferenceRepository;
    private final CollegeCreditRuleRepository collegeCreditRuleRepository;
    private final EmbeddingClient embeddingClient;
    private final PasswordEncoder passwordEncoder;

    public DataImportService(ResourceRepository resourceRepository,
                             UserRepository userRepository,
                             UserProfileRepository userProfileRepository,
                             RagChunkRepository ragChunkRepository,
                             CapabilityTagRepository capabilityTagRepository,
                             CapabilityReferenceRepository capabilityReferenceRepository,
                             CollegeCreditRuleRepository collegeCreditRuleRepository,
                             EmbeddingClient embeddingClient,
                             PasswordEncoder passwordEncoder) {
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.ragChunkRepository = ragChunkRepository;
        this.capabilityTagRepository = capabilityTagRepository;
        this.capabilityReferenceRepository = capabilityReferenceRepository;
        this.collegeCreditRuleRepository = collegeCreditRuleRepository;
        this.embeddingClient = embeddingClient;
        this.passwordEncoder = passwordEncoder;
    }

    public ImportReport run(ImportOptions options) {
        ImportReport report = new ImportReport();
        LOG.info("Import started: {}", options.describe());
        if (options.shouldImportResources()) {
            importResources(options, report);
        }
        if (options.shouldImportProfiles()) {
            importProfiles(options, report);
        }
        if (options.shouldImportRag()) {
            importRag(options, report);
        }
        if (options.shouldImportCapability()) {
            importCapability(options, report);
        }
        if (options.shouldImportCreditRules()) {
            importCreditRules(options, report);
        }
        LOG.info("Import finished: inserted={}, updated={}, skipped={}, failed={}, embeddingFailed={}",
                report.getInserted(), report.getUpdated(), report.getSkipped(),
                report.getFailed(), report.getEmbeddingFailed());
        return report;
    }

    // ------------------------------------------------------------------
    // resources.json
    // ------------------------------------------------------------------

    private void importResources(ImportOptions options, ImportReport report) {
        Path path = options.getResourcesPath();
        JsonNode root = readJson(path, report);
        if (root == null) {
            return;
        }
        if (!root.isArray()) {
            report.error(new ImportError(path.toString(), 0, null, "root node is not an array"));
            return;
        }
        int index = 0;
        Set<String> seenIds = new HashSet<>();
        for (JsonNode node : root) {
            index++;
            if (node == null || node.isNull()) {
                report.failed(new ImportError(path.toString(), index, null, "record is null"));
                continue;
            }
            String key = text(node, "id");
            try {
                String name = text(node, "name");
                String type = text(node, "type");
                if (isBlank(key)) {
                    report.failed(new ImportError(path.toString(), index, null, "missing required field id"));
                    continue;
                }
                if (!seenIds.add(key)) {
                    report.failed(new ImportError(path.toString(), index, key,
                            "duplicate business key id in file"));
                    continue;
                }
                if (isBlank(name)) {
                    report.failed(new ImportError(path.toString(), index, key, "missing required field name"));
                    continue;
                }
                if (type == null || !RESOURCE_TYPES.contains(type)) {
                    report.failed(new ImportError(path.toString(), index, key,
                            type == null ? "missing required field type" : "invalid type: " + type));
                    continue;
                }
                String difficulty = text(node, "difficulty");
                if (difficulty != null && !DIFFICULTIES.contains(difficulty)) {
                    report.failed(new ImportError(path.toString(), index, key, "invalid difficulty: " + difficulty));
                    continue;
                }
                String dataQuality = text(node, "data_quality");
                if (dataQuality != null && !DATA_QUALITIES.contains(dataQuality)) {
                    report.failed(new ImportError(path.toString(), index, key, "invalid data_quality: " + dataQuality));
                    continue;
                }

                Resource resource = new Resource();
                resource.setResourceId(key);
                resource.setName(name);
                resource.setType(type);
                resource.setLevelsJson(jsonString(node, "levels"));
                resource.setStagesJson(jsonString(node, "stages"));
                resource.setTargetMajorsJson(jsonString(node, "target_majors"));
                resource.setRegistrationStart(text(node, "registration_start"));
                resource.setRegistrationDeadline(text(node, "registration_deadline"));
                resource.setRequiredSkillsJson(jsonString(node, "required_skills"));
                resource.setDifficulty(difficulty);
                resource.setPreparationPeriod(text(node, "preparation_period"));
                resource.setTeamRolesJson(jsonString(node, "team_roles"));
                resource.setBonusPointJson(jsonString(node, "bonus_point"));
                resource.setProvider(text(node, "provider"));
                resource.setCourseLink(text(node, "course_link"));
                resource.setDescription(text(node, "description"));
                resource.setTeachesSkillsJson(jsonString(node, "teaches_skills"));
                resource.setSourceUrl(text(node, "source_url"));
                resource.setSourceUrlsJson(jsonString(node, "source_urls"));
                resource.setSourceFile(text(node, "source_file"));
                resource.setNotesJson(jsonString(node, "_notes"));
                resource.setDataQuality(dataQuality == null ? "needs_review" : dataQuality);
                resource.setUpdatedAt(text(node, "updated_at"));

                boolean exists = resourceRepository.findByResourceId(key) != null;
                if (!options.isDryRun()) {
                    resourceRepository.upsert(resource);
                }
                if (exists) {
                    report.updated();
                } else {
                    report.inserted();
                }
            } catch (RuntimeException e) {
                report.failed(new ImportError(path.toString(), index, key, e.getMessage()));
            }
        }
    }

    // ------------------------------------------------------------------
    // student_profiles.json -> demo accounts + profiles
    // ------------------------------------------------------------------

    private void importProfiles(ImportOptions options, ImportReport report) {
        Path path = options.getProfilesPath();
        JsonNode root = readJson(path, report);
        if (root == null) {
            return;
        }
        if (!root.isArray()) {
            report.error(new ImportError(path.toString(), 0, null, "root node is not an array"));
            return;
        }
        int index = 0;
        for (JsonNode node : root) {
            index++;
            if (node == null || node.isNull()) {
                report.failed(new ImportError(path.toString(), index, null, "record is null"));
                continue;
            }
            String username = "testuser_" + index;
            String validationError = validateProfile(node);
            if (validationError != null) {
                report.failed(new ImportError(path.toString(), index, username, validationError));
                continue;
            }
            try {
                User user = userRepository.findByUsername(username);
                if (user == null) {
                    if (!options.isDryRun()) {
                        User created = new User();
                        created.setUsername(username);
                        created.setPasswordHash(passwordEncoder.encode(options.getDemoPassword()));
                        created.setType(UserType.USER);
                        created.setBanned(false);
                        userRepository.insertUser(created);
                        user = userRepository.findByUsername(username);
                    }
                    report.inserted();
                } else {
                    report.skipped();
                }

                if (options.isDryRun() || user == null) {
                    continue;
                }

                UserProfile profile = new UserProfile();
                profile.setUserId(user.getId().longValue());
                profile.setMajor(text(node, "major"));
                profile.setGrade(text(node, "grade"));
                profile.setSkillsJson(jsonString(node, "skills"));
                profile.setInterestsJson(jsonString(node, "interests"));
                profile.setExperiencesJson(jsonString(node, "experiences"));
                profile.setAvailableTime(text(node, "available_time"));
                profile.setGoals(jsonString(node, "goals"));

                boolean profileExists = userProfileRepository.findByUserId(user.getId().longValue()) != null;
                userProfileRepository.upsert(profile);
                if (profileExists) {
                    report.updated();
                } else {
                    report.inserted();
                }
            } catch (RuntimeException e) {
                report.failed(new ImportError(path.toString(), index, username, e.getMessage()));
            }
        }
    }

    /**
     * Sample profiles must be complete enough to create a meaningful demo
     * account; an incomplete object is skipped instead of polluting testuser_N.
     */
    private static String validateProfile(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return "record is not an object";
        }
        if (isBlank(text(node, "major"))) {
            return "missing required field major";
        }
        if (isBlank(text(node, "grade"))) {
            return "missing required field grade";
        }
        if (!isNonEmptyArray(node, "skills")) {
            return "missing or empty required array skills";
        }
        if (!isNonEmptyArray(node, "interests")) {
            return "missing or empty required array interests";
        }
        if (!isNonEmptyArray(node, "experiences")) {
            return "missing or empty required array experiences";
        }
        if (!isNonEmptyArray(node, "goals")) {
            return "missing or empty required array goals";
        }
        return null;
    }

    private static boolean isNonEmptyArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isArray() && !value.isEmpty();
    }

    // ------------------------------------------------------------------
    // rag/chunks.json
    // ------------------------------------------------------------------

    private void importRag(ImportOptions options, ImportReport report) {
        Path path = options.getRagChunksPath();
        JsonNode root = readJson(path, report);
        if (root == null) {
            return;
        }
        if (!root.isArray()) {
            report.error(new ImportError(path.toString(), 0, null, "root node is not an array"));
            return;
        }
        Set<String> knownResourceIds = knownResourceIds(options);
        Set<String> seenChunkIds = new HashSet<>();
        int index = 0;
        for (JsonNode node : root) {
            index++;
            if (node == null || node.isNull()) {
                report.failed(new ImportError(path.toString(), index, null, "record is null"));
                continue;
            }
            String key = text(node, "chunk_id");
            try {
                String title = text(node, "title");
                String text = text(node, "text");
                if (isBlank(key)) {
                    report.failed(new ImportError(path.toString(), index, null, "missing required field chunk_id"));
                    continue;
                }
                if (!seenChunkIds.add(key)) {
                    report.failed(new ImportError(path.toString(), index, key,
                            "duplicate business key chunk_id in file"));
                    continue;
                }
                if (isBlank(title) || isBlank(text)) {
                    report.failed(new ImportError(path.toString(), index, key, "missing required field title/text"));
                    continue;
                }
                String sourceType = text(node, "source_type");
                if (sourceType != null && !SOURCE_TYPES.contains(sourceType)) {
                    report.failed(new ImportError(path.toString(), index, key, "invalid source_type: " + sourceType));
                    continue;
                }
                String relatedResourceId = text(node, "related_resource_id");
                if (relatedResourceId != null && !knownResourceIds.contains(relatedResourceId)) {
                    report.failed(new ImportError(path.toString(), index, key,
                            "related_resource_id not found: " + relatedResourceId));
                    continue;
                }

                RagChunk existing = ragChunkRepository.findById(key);
                String contentHash = ContentHashes.sha256(text);
                boolean unchanged = existing != null
                        && contentHash.equals(existing.getContentHash())
                        && existing.getEmbeddingJson() != null
                        && !existing.getEmbeddingJson().isBlank()
                        && !options.isRebuildEmbeddings();
                if (unchanged) {
                    report.skipped();
                    continue;
                }

                RagChunk chunk = new RagChunk();
                chunk.setChunkId(key);
                chunk.setTitle(title);
                chunk.setText(text);
                chunk.setSourceType(sourceType);
                chunk.setSourceUrl(text(node, "source_url"));
                chunk.setSourceFile(text(node, "source_file"));
                chunk.setPageOrSection(text(node, "page_or_section"));
                chunk.setRelatedResourceId(relatedResourceId);
                chunk.setCreatedAt(text(node, "created_at"));
                chunk.setContentHash(contentHash);
                chunk.setEmbeddingStatus(existing == null ? "PENDING" : existing.getEmbeddingStatus());

                if (!options.isDryRun()) {
                    ragChunkRepository.upsert(chunk);
                    generateEmbedding(options, report, key, text);
                }
                if (existing == null) {
                    report.inserted();
                } else {
                    report.updated();
                }
            } catch (RuntimeException e) {
                report.failed(new ImportError(path.toString(), index, key, e.getMessage()));
            }
        }
    }

    /**
     * Resource ids valid for RAG reference checks. Always includes the current
     * database; in dry-run mode also includes ids present in resources.json so
     * a dry-run import can validate a fresh delivery package.
     */
    private Set<String> knownResourceIds(ImportOptions options) {
        Set<String> ids = new HashSet<>();
        for (Resource resource : resourceRepository.findAll()) {
            if (resource.getResourceId() != null) {
                ids.add(resource.getResourceId());
            }
        }
        if (options.isDryRun() && options.getResourcesPath() != null
                && Files.exists(options.getResourcesPath())) {
            try {
                JsonNode root = MAPPER.readTree(Files.readString(
                        options.getResourcesPath(), StandardCharsets.UTF_8));
                if (root != null && root.isArray()) {
                    for (JsonNode node : root) {
                        String id = text(node, "id");
                        if (id != null) {
                            ids.add(id);
                        }
                    }
                }
            } catch (Exception ignored) {
                // readJson for the resources pass already reports parse errors
            }
        }
        return ids;
    }

    private void generateEmbedding(ImportOptions options, ImportReport report, String chunkId, String text) {
        if (!embeddingClient.isEnabled()) {
            ragChunkRepository.updateEmbedding(chunkId, null, embeddingClient.getModel(), null,
                    "SKIPPED", "embedding disabled", Instant.now().toString());
            report.embeddingFailed(new ImportError("embedding", 0, chunkId, "embedding disabled"));
            return;
        }
        try {
            EmbeddingResult result = embeddingClient.embed(text);
            String vectorJson = MAPPER.writeValueAsString(result.vector());
            ragChunkRepository.updateEmbedding(chunkId, vectorJson, result.model(), result.dimensions(),
                    "SUCCESS", null, Instant.now().toString());
        } catch (EmbeddingException | IOException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (message.length() > 200) {
                message = message.substring(0, 200) + "...(truncated)";
            }
            ragChunkRepository.updateEmbedding(chunkId, null, embeddingClient.getModel(), null,
                    "FAILED", message, Instant.now().toString());
            report.embeddingFailed(new ImportError("embedding", 0, chunkId, message));
        }
    }

    // ------------------------------------------------------------------
    // draft/capability-tags.json
    // ------------------------------------------------------------------

    private void importCapability(ImportOptions options, ImportReport report) {
        Path path = options.getCapabilityTagsPath();
        JsonNode root = readJson(path, report);
        if (root == null) {
            return;
        }
        if (!root.isObject()) {
            report.error(new ImportError(path.toString(), 0, null, "root node is not an object"));
            return;
        }

        JsonNode tags = root.get("tags");
        if (tags != null && tags.isObject()) {
            var fields = tags.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String name = entry.getKey();
                JsonNode node = entry.getValue();
                if (node == null || node.isNull()) {
                    report.failed(new ImportError(path.toString(), 0, name, "record is null"));
                    continue;
                }
                try {
                    String category = text(node, "category");
                    if (category == null || !TAG_CATEGORIES.contains(category)) {
                        report.failed(new ImportError(path.toString(), 0, name,
                                category == null ? "missing tag category" : "invalid tag category: " + category));
                        continue;
                    }
                    CapabilityTag tag = new CapabilityTag();
                    tag.setName(name);
                    tag.setCategory(category);
                    tag.setLevel1Desc(text(node, "L1"));
                    tag.setLevel2Desc(text(node, "L2"));
                    tag.setLevel3Desc(text(node, "L3"));
                    tag.setSkillAliasesJson(jsonString(node, "skill_aliases"));
                    tag.setTypicalEvidenceJson(jsonString(node, "typical_evidence"));
                    boolean exists = capabilityTagRepository.findByName(name) != null;
                    if (!options.isDryRun()) {
                        capabilityTagRepository.upsert(tag);
                    }
                    if (exists) {
                        report.updated();
                    } else {
                        report.inserted();
                    }
                } catch (RuntimeException e) {
                    report.failed(new ImportError(path.toString(), 0, name, e.getMessage()));
                }
            }
        }

        for (String section : REFERENCE_SECTIONS) {
            JsonNode sectionNode = root.get(section);
            if (sectionNode == null || !sectionNode.isObject()) {
                continue;
            }
            var fields = sectionNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String refKey = entry.getKey();
                if ("_说明".equals(refKey)) {
                    continue;
                }
                try {
                    JsonNode value = entry.getValue();
                    if (value == null || value.isNull()) {
                        report.failed(new ImportError(path.toString(), 0, section + "/" + refKey, "record is null"));
                        continue;
                    }
                    String refValue = value.isTextual() ? value.asText() : value.toString();
                    CapabilityReference reference = new CapabilityReference();
                    reference.setSection(section);
                    reference.setRefKey(refKey);
                    reference.setRefValue(refValue);
                    boolean exists = capabilityReferenceRepository.findBySectionAndKey(section, refKey) != null;
                    if (!options.isDryRun()) {
                        capabilityReferenceRepository.upsert(reference);
                    }
                    if (exists) {
                        report.updated();
                    } else {
                        report.inserted();
                    }
                } catch (RuntimeException e) {
                    report.failed(new ImportError(path.toString(), 0, section + "/" + refKey, e.getMessage()));
                }
            }
        }

        JsonNode metaVersion = root.path("_说明").path("version");
        if (!metaVersion.isMissingNode() && !metaVersion.isNull()) {
            CapabilityReference meta = new CapabilityReference();
            meta.setSection("_meta");
            meta.setRefKey("version");
            meta.setRefValue(metaVersion.asText());
            boolean exists = capabilityReferenceRepository.findBySectionAndKey("_meta", "version") != null;
            if (!options.isDryRun()) {
                capabilityReferenceRepository.upsert(meta);
            }
            if (exists) {
                report.updated();
            } else {
                report.inserted();
            }
        }
    }

    // ------------------------------------------------------------------
    // extracted/*.json -> college_credit_rules
    // ------------------------------------------------------------------

    private void importCreditRules(ImportOptions options, ImportReport report) {
        Path dir = options.getCreditRulesDir();
        if (dir == null || !Files.isDirectory(dir)) {
            report.error(new ImportError(String.valueOf(dir), 0, null, "credit rules directory not found"));
            return;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            report.error(new ImportError(dir.toString(), 0, null, "cannot list directory: " + e.getMessage()));
            return;
        }

        for (Path file : files) {
            JsonNode root = readJson(file, report);
            if (root == null) {
                continue;
            }
            JsonNode rules = root.get("rules");
            if (rules == null || !rules.isArray()) {
                report.error(new ImportError(file.toString(), 0, null, "root.rules is not an array"));
                continue;
            }
            int index = 0;
            for (JsonNode node : rules) {
                index++;
                if (node == null || node.isNull()) {
                    report.failed(new ImportError(file.toString(), index, null, "record is null"));
                    continue;
                }
                String key = text(node, "college") + "/" + text(node, "category") + "/" + index;
                try {
                    CollegeCreditRule rule = mapRule(node, file.toString(), index, report);
                    if (rule == null) {
                        continue;
                    }
                    CollegeCreditRule existing = collegeCreditRuleRepository.findMatching(rule);
                    if (!options.isDryRun()) {
                        if (existing == null) {
                            collegeCreditRuleRepository.insert(rule);
                        } else {
                            rule.setId(existing.getId());
                            collegeCreditRuleRepository.update(rule);
                        }
                    }
                    if (existing == null) {
                        report.inserted();
                    } else {
                        report.updated();
                    }
                } catch (RuntimeException e) {
                    report.failed(new ImportError(file.toString(), index, key, e.getMessage()));
                }
            }
        }
    }

    private CollegeCreditRule mapRule(JsonNode node, String file, int index, ImportReport report) {
        String college = text(node, "college");
        String creditType = text(node, "credit_type");
        String category = text(node, "category");
        if (isBlank(college)) {
            report.failed(new ImportError(file, index, null, "missing required field college"));
            return null;
        }
        if (creditType == null || !CREDIT_TYPES.contains(creditType)) {
            report.failed(new ImportError(file, index, college,
                    creditType == null ? "missing required field credit_type" : "invalid credit_type: " + creditType));
            return null;
        }
        if (category == null || !CATEGORIES.contains(category)) {
            report.failed(new ImportError(file, index, college,
                    category == null ? "missing required field category" : "invalid category: " + category));
            return null;
        }
        Double credits = number(node, "credits");
        if (credits == null) {
            report.failed(new ImportError(file, index, college, "missing or invalid numeric field credits"));
            return null;
        }
        String compLevel = text(node, "comp_level");
        if (compLevel != null && !COMP_LEVELS.contains(compLevel)) {
            report.failed(new ImportError(file, index, college, "invalid comp_level: " + compLevel));
            return null;
        }
        String awardTier = text(node, "award_tier");
        if (awardTier != null && !AWARD_TIERS.contains(awardTier)) {
            report.failed(new ImportError(file, index, college, "invalid award_tier: " + awardTier));
            return null;
        }
        Double categoryCap = number(node, "category_cap");
        if (categoryCap == null && node.has("category_cap") && !node.get("category_cap").isNull()) {
            report.failed(new ImportError(file, index, college, "invalid numeric field category_cap"));
            return null;
        }

        CollegeCreditRule rule = new CollegeCreditRule();
        rule.setCollege(college);
        rule.setCreditType(creditType);
        rule.setCategory(category);
        rule.setCompLevel(compLevel);
        rule.setCompName(text(node, "comp_name"));
        rule.setAwardTier(awardTier);
        rule.setCredits(credits);
        rule.setCategoryCap(categoryCap);
        rule.setTeamFormula(text(node, "team_formula"));
        rule.setStudentCohort(text(node, "student_cohort"));
        rule.setDocSource(text(node, "doc_source"));
        rule.setLevelsJson(jsonString(node, "levels"));
        rule.setNotes(text(node, "_notes"));
        return rule;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private JsonNode readJson(Path path, ImportReport report) {
        if (path == null) {
            report.error(new ImportError("null", 0, null, "no input path configured"));
            return null;
        }
        if (!Files.exists(path)) {
            report.error(new ImportError(path.toString(), 0, null, "file not found"));
            return null;
        }
        try {
            return MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception e) {
            report.error(new ImportError(path.toString(), 0, null, "invalid JSON: " + e.getMessage()));
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String jsonString(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static Double number(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
