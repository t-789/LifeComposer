package org.example.lifecomposer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LifeComposerApplication {

    public static void main(String[] args) {
        // FIX: Do not keep a global static JDBC Connection in application code.
        // Let Spring DataSource manage connection lifecycle and thread safety.
        SpringApplication.run(LifeComposerApplication.class, args);
    }
}
