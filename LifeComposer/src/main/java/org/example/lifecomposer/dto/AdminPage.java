package org.example.lifecomposer.dto;

/**
 * Resolved paging specification for admin console queries. {@code orderBy} is
 * always built from a server-side whitelist, never from raw request input.
 */
public record AdminPage(int page, int pageSize, String orderBy) {

    public int offset() {
        return (page - 1) * pageSize;
    }

    public int limit() {
        return pageSize;
    }
}
