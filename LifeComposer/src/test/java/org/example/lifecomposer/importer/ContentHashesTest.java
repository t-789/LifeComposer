package org.example.lifecomposer.importer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentHashesTest {

    @Test
    void sha256IsDeterministic() {
        String first = ContentHashes.sha256("数学建模");
        String second = ContentHashes.sha256("数学建模");
        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void sha256ChangesWithText() {
        assertNotEquals(ContentHashes.sha256("a"), ContentHashes.sha256("b"));
    }
}
