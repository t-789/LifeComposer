package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.RagChunk;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class RagChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public RagChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<RagChunk> RAG_CHUNK_ROW_MAPPER = new RowMapper<>() {
        @Override
        public RagChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            RagChunk chunk = new RagChunk();
            chunk.setChunkId(rs.getString("chunk_id"));
            chunk.setTitle(rs.getString("title"));
            chunk.setText(rs.getString("text"));
            chunk.setSourceType(rs.getString("source_type"));
            chunk.setSourceUrl(rs.getString("source_url"));
            chunk.setSourceFile(rs.getString("source_file"));
            chunk.setPageOrSection(rs.getString("page_or_section"));
            chunk.setRelatedResourceId(rs.getString("related_resource_id"));
            chunk.setCreatedAt(rs.getString("created_at"));
            return chunk;
        }
    };

    public List<RagChunk> findAll() {
        String sql = """
                SELECT * FROM rag_chunks
                ORDER BY chunk_id
                """;
        return jdbcTemplate.query(sql, RAG_CHUNK_ROW_MAPPER);
    }

    /** 按业务键 resources.resource_id 过滤切片（related_resource_id 可为 NULL，过滤时不返回）。 */
    public List<RagChunk> findByRelatedResourceId(String relatedResourceId) {
        String sql = """
                SELECT * FROM rag_chunks
                WHERE related_resource_id = ?
                ORDER BY chunk_id
                """;
        return jdbcTemplate.query(sql, RAG_CHUNK_ROW_MAPPER, relatedResourceId);
    }

    /** 按切片主键 chunk_id 查找（如 rag_001），找不到返回 null。 */
    public RagChunk findById(String chunkId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM rag_chunks WHERE chunk_id = ?",
                    RAG_CHUNK_ROW_MAPPER, chunkId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按 chunk_id 幂等写入（重复导入同一交付包不产生重复行）。 */
    public int upsert(RagChunk chunk) {
        String sql = """
                INSERT INTO rag_chunks
                    (chunk_id, title, text, source_type, source_url,
                     source_file, page_or_section, related_resource_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(chunk_id) DO UPDATE SET
                    title = excluded.title,
                    text = excluded.text,
                    source_type = excluded.source_type,
                    source_url = excluded.source_url,
                    source_file = excluded.source_file,
                    page_or_section = excluded.page_or_section,
                    related_resource_id = excluded.related_resource_id,
                    created_at = excluded.created_at
                """;
        return jdbcTemplate.update(sql,
                chunk.getChunkId(),
                chunk.getTitle(),
                chunk.getText(),
                chunk.getSourceType(),
                chunk.getSourceUrl(),
                chunk.getSourceFile(),
                chunk.getPageOrSection(),
                chunk.getRelatedResourceId(),
                chunk.getCreatedAt());
    }

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS rag_chunks (
                  chunk_id            TEXT PRIMARY KEY,
                  title               TEXT NOT NULL,
                  text                TEXT NOT NULL,
                  source_type         TEXT CHECK(source_type IN ('web', 'pdf', 'json')),
                  source_url          TEXT,
                  source_file         TEXT,
                  page_or_section     TEXT,
                  related_resource_id TEXT,
                  created_at          TEXT
                )
                """;
        jdbcTemplate.execute(sql);

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rag_related ON rag_chunks(related_resource_id)");
    }
}
