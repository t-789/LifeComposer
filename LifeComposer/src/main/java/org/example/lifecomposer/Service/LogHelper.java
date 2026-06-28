package org.example.lifecomposer.Service;

import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogHelper {

    private LogHelper() {
        // utility class
    }

    public static void logError(Logger logger, FeedbackService feedbackService, String content, Throwable throwable, String url) {
        logger.error(content, throwable);

        if (feedbackService == null) {
            return;
        }

        String feedbackContent = content;
        if (throwable != null) {
            feedbackContent = content + "\nException: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }

        try {
            feedbackService.saveSystemFeedback(null, "system", feedbackContent, url, null, extractStackTrace(throwable));
        } catch (Exception e) {
            logger.warn("Failed to record error to feedback system: " + e.getMessage());
        }
    }

    public static void logFatal(Logger logger, FeedbackService feedbackService, String content, Throwable throwable, String url) {
        logger.fatal(content, throwable);

        if (feedbackService == null) {
            return;
        }

        String feedbackContent = content;
        if (throwable != null) {
            feedbackContent = content + "\nException: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }

        try {
            feedbackService.saveSystemFeedback(null, "system", feedbackContent, url, null, extractStackTrace(throwable));
        } catch (Exception e) {
            logger.warn("Failed to record error to feedback system: " + e.getMessage());
        }
    }

    private static String extractStackTrace(Throwable t) {
        if (t == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        String stackTrace = sw.toString();
        return stackTrace.length() > 2000 ? stackTrace.substring(0, 2000) : stackTrace;
    }
}
