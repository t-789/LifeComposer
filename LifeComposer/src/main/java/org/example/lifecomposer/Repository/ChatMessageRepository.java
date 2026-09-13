package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ChatMessage> ROW_MAPPER = new RowMapper<>() {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage msg = new ChatMessage();
            msg.setId(rs.getInt("id"));
            msg.setUserId(rs.getInt("user_id"));
            msg.setRole(rs.getString("role"));
            msg.setContent(rs.getString("content"));
            msg.setCreateTime(rs.getTimestamp("create_time"));
            return msg;
        }
    };

    public void createChatMessageTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS chat_messages (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NOT NULL,
                  role TEXT NOT NULL,
                  content TEXT NOT NULL,
                  create_time TIMESTAMP NOT NULL
                )
                """;
        jdbcTemplate.execute(sql);
    }

    public int saveMessage(ChatMessage msg) {
        String sql = "INSERT INTO chat_messages(user_id, role, content, create_time) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, msg.getUserId());
            ps.setString(2, msg.getRole());
            ps.setString(3, msg.getContent());
            ps.setTimestamp(4, msg.getCreateTime());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    /** Atomically insert a tool_call + tool_result pair (or similar batch). */
    @Transactional
    public void saveMessages(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            saveMessage(message);
        }
    }

    public List<ChatMessage> findByUserId(Integer userId) {
        return jdbcTemplate.query(
                "SELECT * FROM chat_messages WHERE user_id = ? ORDER BY create_time ASC, id ASC",
                ROW_MAPPER, userId);
    }

    public int deleteByUserId(Integer userId) {
        return jdbcTemplate.update("DELETE FROM chat_messages WHERE user_id = ?", userId);
    }
}
