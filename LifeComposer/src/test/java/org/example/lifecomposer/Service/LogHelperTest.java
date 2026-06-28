package org.example.lifecomposer.Service;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogHelperTest {

    @Test
    void logError_callsLoggerAndFeedbackService() {
        Logger logger = mock(Logger.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        Throwable throwable = new RuntimeException("test error");

        LogHelper.logError(logger, feedbackService, "Something went wrong", throwable, "http://example.com");

        verify(logger, times(1)).error("Something went wrong", throwable);
        verify(feedbackService, times(1)).saveSystemFeedback(
                eq(null), eq("system"), contains("Something went wrong"), eq("http://example.com"), eq(null), anyString()
        );
    }

    @Test
    void logError_nullFeedbackService_skipsFeedback() {
        Logger logger = mock(Logger.class);
        Throwable throwable = new RuntimeException("test error");

        assertDoesNotThrow(() ->
                LogHelper.logError(logger, null, "Something went wrong", throwable, "http://example.com")
        );

        verify(logger, times(1)).error("Something went wrong", throwable);
    }

    @Test
    void logError_feedbackServiceThrows_caughtAndLogged() {
        Logger logger = mock(Logger.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        Throwable throwable = new RuntimeException("test error");

        when(feedbackService.saveSystemFeedback(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertDoesNotThrow(() ->
                LogHelper.logError(logger, feedbackService, "Something went wrong", throwable, "http://example.com")
        );

        verify(logger, times(1)).error("Something went wrong", throwable);
        verify(logger, times(1)).warn(contains("Failed to record error to feedback system"));
    }

    @Test
    void logFatal_callsLoggerFatal() {
        Logger logger = mock(Logger.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        Throwable throwable = new RuntimeException("fatal error");

        LogHelper.logFatal(logger, feedbackService, "Fatal error occurred", throwable, "http://example.com");

        verify(logger, times(1)).fatal("Fatal error occurred", throwable);
        verify(feedbackService, times(1)).saveSystemFeedback(
                eq(null), eq("system"), contains("Fatal error occurred"), eq("http://example.com"), eq(null), anyString()
        );
    }

    @Test
    void extractStackTrace_null_returnsNull() {
        // extractStackTrace is private; we test it indirectly
        Logger logger = mock(Logger.class);
        FeedbackService feedbackService = mock(FeedbackService.class);

        LogHelper.logError(logger, feedbackService, "No throwable", null, "http://example.com");

        verify(feedbackService, times(1)).saveSystemFeedback(
                eq(null), eq("system"), eq("No throwable"), eq("http://example.com"), eq(null), eq(null)
        );
    }

    @Test
    void logError_withNullThrowable_stillWorks() {
        Logger logger = mock(Logger.class);
        FeedbackService feedbackService = mock(FeedbackService.class);

        LogHelper.logError(logger, feedbackService, "Something happened", null, "http://example.com");

        verify(logger, times(1)).error("Something happened", (Throwable) null);
        verify(feedbackService, times(1)).saveSystemFeedback(
                eq(null), eq("system"), eq("Something happened"), eq("http://example.com"), eq(null), eq(null)
        );
    }
}
