package org.example.lifecomposer.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.RagChunk;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.config.RagConfig;
import org.example.lifecomposer.embedding.EmbeddingClient;
import org.example.lifecomposer.embedding.EmbeddingException;
import org.example.lifecomposer.embedding.EmbeddingResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RAG retrieval over SQLite-stored JSON vectors. Chunks without a successful
 * embedding are excluded. Defaults: topK=5, min similarity=0.2, order by
 * similarity desc then chunk_id asc.
 */
@Service
public class RagSearchService {

    private static final Logger LOG = LogManager.getLogger(RagSearchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmbeddingClient embeddingClient;
    private final RagChunkRepository ragChunkRepository;
    private final RagConfig ragConfig;

    public RagSearchService(EmbeddingClient embeddingClient,
                            RagChunkRepository ragChunkRepository,
                            RagConfig ragConfig) {
        this.embeddingClient = embeddingClient;
        this.ragChunkRepository = ragChunkRepository;
        this.ragConfig = ragConfig;
    }

    public List<RagHit> search(String query, Integer requestedTopK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int topK = requestedTopK == null || requestedTopK <= 0
                ? ragConfig.getDefaultTopK()
                : Math.min(requestedTopK, ragConfig.getMaxTopK());

        EmbeddingResult queryEmbedding;
        try {
            queryEmbedding = embeddingClient.embed(query);
        } catch (EmbeddingException e) {
            throw new RagSearchException("embedding unavailable: " + e.getMessage(), e);
        }

        List<RagChunk> chunks = ragChunkRepository.findAllWithEmbedding();
        List<RagHit> hits = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            double[] vector = parseVector(chunk.getEmbeddingJson());
            if (vector == null || vector.length != queryEmbedding.dimensions()) {
                continue;
            }
            double similarity = cosine(queryEmbedding.vector(), vector);
            if (similarity < ragConfig.getMinSimilarity()) {
                continue;
            }
            hits.add(new RagHit(
                    chunk.getChunkId(),
                    chunk.getTitle(),
                    chunk.getText(),
                    chunk.getSourceType(),
                    chunk.getSourceUrl(),
                    chunk.getSourceFile(),
                    chunk.getPageOrSection(),
                    chunk.getRelatedResourceId(),
                    similarity));
        }

        hits.sort(Comparator
                .comparingDouble(RagHit::similarity).reversed()
                .thenComparing(RagHit::chunkId));
        if (hits.size() > topK) {
            return new ArrayList<>(hits.subList(0, topK));
        }
        return hits;
    }

    private double[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, double[].class);
        } catch (Exception e) {
            LOG.warn("Skipping RAG chunk with invalid embedding JSON: {}", e.getMessage());
            return null;
        }
    }

    private double cosine(List<Double> a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < b.length; i++) {
            double av = a.get(i);
            double bv = b[i];
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
