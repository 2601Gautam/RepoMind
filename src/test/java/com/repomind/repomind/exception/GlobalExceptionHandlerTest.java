package com.repomind.repomind.exception;

import com.repomind.repomind.service.ingestion.EmbeddingRetryPolicy;
import com.repomind.repomind.service.ingestion.EmbeddingRetryPolicy.Kind;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void exhaustedEmbeddingRetriesAreAServiceUnavailableNotABadRequest() {
        var cause = new NonTransientAiException("HTTP 429 - {\"message\":\"provider secret detail\"}");
        var response = handler.handleEmbeddingRetryExhausted(new EmbeddingRetryPolicy.RetryExhaustedException(Kind.RATE_LIMITED, 3, cause));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("AI_SERVICE_UNAVAILABLE", response.getBody().get("error"));
        assertFalse(String.valueOf(response.getBody()).contains("provider secret detail"), "provider text must not reach the client");
    }

    @Test void aProviderClientErrorStillMapsToServiceUnavailable() {
        var response = handler.handleAiProviderFailure(new NonTransientAiException("HTTP 401 - invalid key"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(String.valueOf(response.getBody()).contains("invalid key"));
    }

    @Test void ordinaryRuntimeErrorsRemainBadRequests() {
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleRuntime(new RuntimeException("Repository not found")).getStatusCode());
    }
}
