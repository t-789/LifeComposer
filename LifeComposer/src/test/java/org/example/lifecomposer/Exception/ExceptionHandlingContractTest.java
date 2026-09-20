package org.example.lifecomposer.Exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.apache.catalina.connector.ClientAbortException;
import org.example.lifecomposer.Service.FeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.Valid;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 2 contract matrix (v0.0.7 audit remediation).
 *
 * <p>Pins status code, body shape, page/API routing and feedback-writing side
 * effects for every exception category so the advice split cannot silently
 * change existing API behaviour.
 */
class ExceptionHandlingContractTest {

    private static final String UA = "TestClient/1.0";

    private FeedbackService feedbackService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);
        GlobalExceptionHandler global = new GlobalExceptionHandler(feedbackService);
        ValidationExceptionHandler validation = new ValidationExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                // validation advice first: exact handlers must win over the catch-all
                .setControllerAdvice(validation, global)
                .build();
    }

    // ---------------------------------------------------------------- input errors

    @Test
    @DisplayName("missing JSON body -> 400 plain text, no system feedback")
    void missingJsonBodyIsClientError() throws Exception {
        mockMvc.perform(post("/api/probe/body")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoFeedback();
    }

    @Test
    @DisplayName("malformed JSON -> 400 plain text from the input-error advice, no feedback")
    void malformedJsonBodyIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/probe/body")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn();

        assertTrue(result.getResponse().getContentLength() > 0, "expected a plain-text message body");
        verifyNoFeedback();
    }

    @Test
    @DisplayName("the unreadable-body message itself stays stable")
    void unreadableBodyKeepsExactMessage() {
        ValidationExceptionHandler handler = new ValidationExceptionHandler();
        MockHttpServletRequest probe = new MockHttpServletRequest("POST", "/api/probe/body");
        probe.addHeader("User-Agent", UA);

        ResponseEntity<?> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])),
                probe);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("请求体缺失或格式不正确", response.getBody());
    }

    @Test
    @DisplayName("Bean Validation -> 400 field map, no feedback")
    void beanValidationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/probe/body")
                        .header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("name 不能为空"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("type conversion failure -> 400, no feedback")
    void typeMismatchIsClientError() throws Exception {
        mockMvc.perform(get("/api/probe/typed").param("value", "abc").header("User-Agent", UA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Method argument type mismatch"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("missing required parameter -> 400, no feedback")
    void missingParameterIsClientError() throws Exception {
        mockMvc.perform(get("/api/probe/required").header("User-Agent", UA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Missing servlet request parameter."));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("unsupported media type -> 400, no feedback")
    void unsupportedMediaTypeIsClientError() throws Exception {
        mockMvc.perform(post("/api/probe/body")
                        .header("User-Agent", UA)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unsupported media type"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("method-level ConstraintViolationException -> 400, no feedback")
    void constraintViolationIsClientError() throws Exception {
        mockMvc.perform(get("/api/probe/violation").header("User-Agent", UA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid input"));
        verifyNoFeedback();
    }

    // ---------------------------------------------------------------- server errors

    @Test
    @DisplayName("unknown exception -> 500 and exactly one system feedback row")
    void unknownExceptionWritesFeedback() throws Exception {
        mockMvc.perform(get("/api/probe/boom").header("User-Agent", UA))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Internal Server Error"));

        verify(feedbackService, times(1)).saveSystemFeedback(
                any(), any(), org.mockito.ArgumentMatchers.eq("boom"), any(), any(), any());
    }

    @Test
    @DisplayName("feedback write failure must not replace the original 500")
    void feedbackWriteFailureKeepsOriginalError() throws Exception {
        doThrow(new RuntimeException("feedback down"))
                .when(feedbackService).saveSystemFeedback(any(), any(), any(), any(), any(), any());

        mockMvc.perform(get("/api/probe/boom").header("User-Agent", UA))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Internal Server Error"));
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("async request not usable keeps 500 but writes no feedback")
    void asyncRequestNotUsableDoesNotWriteFeedback() throws Exception {
        mockMvc.perform(get("/api/probe/disconnect").header("User-Agent", UA))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Internal Server Error"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("servlet client abort -> 499 client-closed-request, not a 200 or a 400")
    void clientAbortUsesExplicitClientClosedStatus() throws Exception {
        mockMvc.perform(get("/api/probe/abort").header("User-Agent", UA))
                .andExpect(status().is(499))
                .andExpect(content().string("ClientAbortException"));
        verifyNoFeedback();
    }

    // ---------------------------------------------------------------- routing

    @Test
    @DisplayName("unknown /api path -> bare 404, no feedback")
    void unknownApiPathReturns404() throws Exception {
        mockMvc.perform(get("/api/probe/missing").header("User-Agent", UA))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("unknown page path -> 302 /error/404, no feedback")
    void unknownPagePathRedirects() throws Exception {
        mockMvc.perform(get("/page/probe/missing").header("User-Agent", UA))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/error/404"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("unsupported HTTP method keeps the API 404 contract")
    void unsupportedMethodKeepsApiContract() throws Exception {
        mockMvc.perform(post("/api/probe/typed").header("User-Agent", UA))
                .andExpect(status().isNotFound());
        verifyNoFeedback();
    }

    @Test
    @DisplayName("bot-looking user agent -> 403, no feedback")
    void botRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/probe/missing").header("User-Agent", "python-requests/2.31"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("empty user agent -> 403, no feedback")
    void emptyUserAgentIsRejected() throws Exception {
        mockMvc.perform(get("/api/probe/missing").header("User-Agent", ""))
                .andExpect(status().isForbidden());
        verifyNoFeedback();
    }

    @Test
    @DisplayName("bot cannot bypass the 403 by sending a malformed JSON body")
    void botCannotBypassWithMalformedBody() throws Exception {
        mockMvc.perform(post("/api/probe/body")
                        .header("User-Agent", "python-requests/2.31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("bot cannot bypass the 403 by omitting a required parameter")
    void botCannotBypassWithMissingParameter() throws Exception {
        mockMvc.perform(get("/api/probe/required").header("User-Agent", ""))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("bot cannot bypass the 403 via the client-abort handler")
    void botCannotBypassWithClientAbort() throws Exception {
        mockMvc.perform(get("/api/probe/abort").header("User-Agent", "python-requests/2.31"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
        verifyNoFeedback();
    }

    @Test
    @DisplayName("bot cannot bypass the 403 via the async-not-usable handler")
    void botCannotBypassWithAsyncRequestNotUsable() throws Exception {
        mockMvc.perform(get("/api/probe/disconnect").header("User-Agent", ""))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
        verifyNoFeedback();
    }

    private void verifyNoFeedback() {
        verify(feedbackService, never()).saveSystemFeedback(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- probe fixtures

    static class ProbeBody {
        @NotBlank(message = "name 不能为空")
        public String name;
    }

    @RestController
    static class ProbeController {

        @PostMapping(value = "/api/probe/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public String body(@Valid @RequestBody ProbeBody body) {
            return "ok";
        }

        @GetMapping("/api/probe/typed")
        public String typed(@RequestParam int value) {
            return "v=" + value;
        }

        @GetMapping("/api/probe/required")
        public String required(@RequestParam String q) {
            return q;
        }

        @GetMapping("/api/probe/violation")
        public String violation() {
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            Set<ConstraintViolation<ProbeBody>> violations = validator.validate(new ProbeBody());
            assertEquals(1, violations.size());
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/api/probe/boom")
        public String boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/api/probe/disconnect")
        public String disconnect() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("client gone");
        }

        @GetMapping("/api/probe/abort")
        public String abort() throws ClientAbortException {
            throw new ClientAbortException("client abort");
        }

        @GetMapping("/api/probe/missing")
        public String missingApi() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/probe/missing", "No static resource");
        }

        @GetMapping("/page/probe/missing")
        public String missingPage() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/page/probe/missing", "No static resource");
        }
    }
}
