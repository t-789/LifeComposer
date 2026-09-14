package org.example.lifecomposer.support;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only Log4j2 appender that records already-formatted messages so tests can
 * assert on AUDIT lines (e.g. "the audit entry must not contain the password").
 *
 * <p>Attach it to the {@code org.example.lifecomposer} logger which is configured
 * with {@code additivity=false} in log4j2-spring.xml.</p>
 */
public final class AuditLogCapture extends AbstractAppender implements AutoCloseable {

    private static final String LOGGER_NAME = "org.example.lifecomposer";
    private static final String APPENDER_NAME = "test-audit-capture";

    private final List<String> messages = new CopyOnWriteArrayList<>();

    public AuditLogCapture() {
        super(APPENDER_NAME, null, null, true, null);
        start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        configuration.getLoggerConfig(LOGGER_NAME).addAppender(this, Level.INFO, null);
        context.updateLoggers();
    }

    @Override
    public void append(LogEvent event) {
        messages.add(event.getMessage().getFormattedMessage());
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    public boolean contains(String fragment) {
        return messages.stream().anyMatch(message -> message.contains(fragment));
    }

    /** Concatenation of all captured lines, for negative assertions. */
    public String joined() {
        return String.join("\n", messages);
    }

    @Override
    public void close() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getLoggerConfig(LOGGER_NAME).removeAppender(APPENDER_NAME);
        context.updateLoggers();
    }
}
