package com.repomind.repomind.dto.queue;

import java.util.UUID;

/**
 * The durable payload sent from the API process to the ingestion worker.
 *
 * The GitHub token is required only for private-repository clones. The SQS
 * queue must have server-side encryption enabled, and the message body must
 * never be logged.
 */
public record IngestionJobMessage(
        UUID repoId,
        String githubUrl,
        String token
) {
}
