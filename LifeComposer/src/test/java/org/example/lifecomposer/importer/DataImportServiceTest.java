package org.example.lifecomposer.importer;

import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.embedding.EmbeddingClient;
import org.example.lifecomposer.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void importsValidRecordsAndSkipsBadOnes() throws Exception {
        writeSampleFiles();

        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        Resource known = new Resource();
        known.setResourceId("competition_001");
        when(resourceRepository.findAll()).thenReturn(List.of(known));
        when(resourceRepository.findByResourceId(anyString())).thenReturn(known);

        UserRepository userRepository = mock(UserRepository.class);
        User createdUser = new User();
        createdUser.setId(42);
        createdUser.setUsername("testuser_1");
        when(userRepository.findByUsername("testuser_1")).thenReturn(null, createdUser);

        UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
        when(userProfileRepository.findByUserId(42L)).thenReturn(null);

        RagChunkRepository ragChunkRepository = mock(RagChunkRepository.class);
        when(ragChunkRepository.findById(anyString())).thenReturn(null);

        CapabilityTagRepository capabilityTagRepository = mock(CapabilityTagRepository.class);
        CapabilityReferenceRepository capabilityReferenceRepository = mock(CapabilityReferenceRepository.class);
        CollegeCreditRuleRepository collegeCreditRuleRepository = mock(CollegeCreditRuleRepository.class);
        when(collegeCreditRuleRepository.findMatching(any())).thenReturn(null);

        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.isEnabled()).thenReturn(true);
        when(embeddingClient.getModel()).thenReturn("nomic-embed-text-v2-moe:latest");
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResult(List.of(0.1, 0.2), "nomic-embed-text-v2-moe:latest", 2));

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        DataImportService service = new DataImportService(
                resourceRepository,
                userRepository,
                userProfileRepository,
                ragChunkRepository,
                capabilityTagRepository,
                capabilityReferenceRepository,
                collegeCreditRuleRepository,
                embeddingClient,
                passwordEncoder);

        ImportOptions options = ImportOptions.parse(new String[]{"--import-dir=" + tempDir});
        ImportReport report = service.run(options);

        // 1 resource + 1 user + 1 profile + 1 rag chunk + 1 tag + 1 ref + 1 credit rule
        assertEquals(7, report.getInserted());
        // missing name, invalid type, invalid rag reference, invalid credit rule
        assertEquals(4, report.getFailed());
        assertEquals(0, report.getEmbeddingFailed());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.reason().contains("missing required field name")));
        assertTrue(report.getErrors().stream().anyMatch(e -> e.reason().contains("invalid type")));
        assertTrue(report.getErrors().stream().anyMatch(e -> e.reason().contains("related_resource_id not found")));
        assertTrue(report.getErrors().stream().anyMatch(e -> e.reason().contains("credits")));

        verify(ragChunkRepository).upsert(any());
        verify(ragChunkRepository).updateEmbedding(
                eq("rag_001"), anyString(), eq("nomic-embed-text-v2-moe:latest"),
                eq(2), eq("SUCCESS"), isNull(), anyString());
    }

    @Test
    void skipsIncompleteProfileWithoutCreatingDemoUser() throws Exception {
        Path data = Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(data.resolve("student_profiles.json"), "[{}]", StandardCharsets.UTF_8);

        UserRepository userRepository = mock(UserRepository.class);
        UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);

        DataImportService service = new DataImportService(
                mock(ResourceRepository.class),
                userRepository,
                userProfileRepository,
                mock(RagChunkRepository.class),
                mock(CapabilityTagRepository.class),
                mock(CapabilityReferenceRepository.class),
                mock(CollegeCreditRuleRepository.class),
                mock(EmbeddingClient.class),
                mock(PasswordEncoder.class));

        ImportOptions options = ImportOptions.parse(new String[]{"--import-dir=" + tempDir, "--profiles"});
        ImportReport report = service.run(options);

        assertEquals(1, report.getFailed());
        assertTrue(report.hasErrors());
        assertTrue(report.getErrors().get(0).reason().contains("major"));
        verify(userRepository, never()).insertUser(any());
        verify(userProfileRepository, never()).upsert(any());
    }

    @Test
    void skipsNullRecordsAndContinues() throws Exception {
        Path data = Files.createDirectories(tempDir.resolve("data"));
        Path rag = Files.createDirectories(tempDir.resolve("rag"));
        Files.writeString(data.resolve("resources.json"), """
                [
                  null,
                  {"id":"competition_001","name":"A","type":"competition"}
                ]
                """, StandardCharsets.UTF_8);
        Files.writeString(data.resolve("student_profiles.json"), """
                [
                  null,
                  {"major":"计算机科学与技术","grade":"大一","skills":["Python"],
                   "interests":["AI"],"experiences":["课设"],"goals":["了解竞赛"]}
                ]
                """, StandardCharsets.UTF_8);
        Files.writeString(rag.resolve("chunks.json"), """
                [
                  null,
                  {"chunk_id":"rag_001","title":"T","text":"X"}
                ]
                """, StandardCharsets.UTF_8);

        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        when(resourceRepository.findAll()).thenReturn(List.of());

        DataImportService service = new DataImportService(
                resourceRepository,
                mock(UserRepository.class),
                mock(UserProfileRepository.class),
                mock(RagChunkRepository.class),
                mock(CapabilityTagRepository.class),
                mock(CapabilityReferenceRepository.class),
                mock(CollegeCreditRuleRepository.class),
                mock(EmbeddingClient.class),
                mock(PasswordEncoder.class));

        ImportOptions options = ImportOptions.parse(new String[]{
                "--import-dir=" + tempDir, "--resources", "--profiles", "--rag-chunks", "--dry-run"});
        ImportReport report = service.run(options);

        assertEquals(3, report.getFailed());
        assertEquals(3, report.getErrors().stream()
                .filter(error -> "record is null".equals(error.reason()))
                .count());
    }

    @Test
    void reportsDuplicateBusinessKeysInSameFile() throws Exception {
        Path data = Files.createDirectories(tempDir.resolve("data"));
        Path rag = Files.createDirectories(tempDir.resolve("rag"));
        Files.writeString(data.resolve("resources.json"), """
                [
                  {"id":"competition_001","name":"A","type":"competition"},
                  {"id":"competition_001","name":"B","type":"competition"}
                ]
                """, StandardCharsets.UTF_8);
        Files.writeString(rag.resolve("chunks.json"), """
                [
                  {"chunk_id":"rag_001","title":"T","text":"X"},
                  {"chunk_id":"rag_001","title":"T2","text":"Y"}
                ]
                """, StandardCharsets.UTF_8);

        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        when(resourceRepository.findAll()).thenReturn(List.of());

        DataImportService service = new DataImportService(
                resourceRepository,
                mock(UserRepository.class),
                mock(UserProfileRepository.class),
                mock(RagChunkRepository.class),
                mock(CapabilityTagRepository.class),
                mock(CapabilityReferenceRepository.class),
                mock(CollegeCreditRuleRepository.class),
                mock(EmbeddingClient.class),
                mock(PasswordEncoder.class));

        ImportOptions options = ImportOptions.parse(new String[]{
                "--import-dir=" + tempDir, "--resources", "--rag-chunks", "--dry-run"});
        ImportReport report = service.run(options);

        assertEquals(2, report.getFailed());
        assertTrue(report.hasErrors());
        assertTrue(report.getErrors().stream()
                .anyMatch(error -> error.reason().contains("duplicate business key id")));
        assertTrue(report.getErrors().stream()
                .anyMatch(error -> error.reason().contains("duplicate business key chunk_id")));
    }

    private void writeSampleFiles() throws Exception {
        Path data = Files.createDirectories(tempDir.resolve("data"));
        Path rag = Files.createDirectories(tempDir.resolve("rag"));
        Path draft = Files.createDirectories(tempDir.resolve("draft"));
        Path extracted = Files.createDirectories(tempDir.resolve("extracted"));

        Files.writeString(data.resolve("resources.json"), """
                [
                  {"id":"competition_001","name":"数学建模竞赛","type":"competition","difficulty":"medium"},
                  {"id":"competition_002","type":"competition"},
                  {"id":"competition_003","name":"坏类型","type":"not-a-type"}
                ]
                """, StandardCharsets.UTF_8);

        Files.writeString(data.resolve("student_profiles.json"), """
                [
                  {"_说明":"demo","major":"计算机科学与技术","grade":"大一",
                   "skills":["Python"],"interests":["AI"],"experiences":["课设"],
                   "available_time":"6h/week","goals":["了解竞赛"]}
                ]
                """, StandardCharsets.UTF_8);

        Files.writeString(rag.resolve("chunks.json"), """
                [
                  {"chunk_id":"rag_001","title":"有效切片","text":"数学建模文本","source_type":"web",
                   "related_resource_id":"competition_001"},
                  {"chunk_id":"rag_002","title":"坏引用","text":"文本","source_type":"web",
                   "related_resource_id":"missing_resource"}
                ]
                """, StandardCharsets.UTF_8);

        Files.writeString(draft.resolve("capability-tags.json"), """
                {
                  "_说明":{"version":"1.1"},
                  "tags":{"编程基础":{"category":"技术能力","L1":"L1","L2":"L2","L3":"L3",
                    "skill_aliases":["Python"],"typical_evidence":["课设"]}},
                  "skill_mapping":{"Python":["编程基础"]}
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(extracted.resolve("rules.json"), """
                {"rules":[
                  {"college":"计算机学院","credit_type":"recommendation","category":"competition",
                   "comp_level":"S","award_tier":"first","credits":3.0,"levels":["national"]},
                  {"college":"计算机学院","credit_type":"recommendation","category":"competition",
                   "credits":"not-a-number"}
                ]}
                """, StandardCharsets.UTF_8);
    }
}
