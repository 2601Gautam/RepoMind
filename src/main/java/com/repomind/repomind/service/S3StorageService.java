package com.repomind.repomind.service;

import com.repomind.repomind.service.ingestion.FileCloneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Archives the extracted source of every ingested repository to S3, and
// hands out short-lived signed URLs to download that archive later.
//
// Why: once ingestion finishes, RepoMind deletes the cloned repo from local
// disk (see IngestionService's finally block) to avoid filling up server
// storage. Before that happens, this service uploads a zip of exactly what
// was indexed to S3 — so a repo can be re-embedded later (new chunking
// strategy, new embedding model) without re-cloning from GitHub, and users
// get a downloadable snapshot of what RepoMind actually read.
@Service
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Zips every extracted source file for a repository and uploads it to
     * s3://{bucket}/repos/{repoId}/source.zip
     * <p>
     * Returns the S3 object key on success, or null if anything went wrong.
     * Archival is a nice-to-have on top of ingestion, not a requirement —
     * an S3 outage, a missing bucket, or a permissions issue must never
     * fail the ingestion pipeline itself.
     */
    public String uploadRepoArchive(UUID repoId, List<FileCloneService.ParsedFile> files) {
        String key = "repos/" + repoId + "/source.zip";
        try {
            byte[] zipBytes = zipFiles(files);

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType("application/zip")
                            .build(),
                    RequestBody.fromBytes(zipBytes)
            );

            log.info("Uploaded repo archive to s3://{}/{} ({} bytes)", bucketName, key, zipBytes.length);
            return key;
        } catch (Exception e) {
            log.warn("Skipping S3 archive for repo {}: {}", repoId, e.getMessage());
            return null;
        }
    }

    /**
     * Generates a temporary (15-minute) signed download URL for a private
     * S3 object. The bucket stays fully private the whole time — nothing
     * is ever made public to get this to work.
     */
    public String generatePresignedDownloadUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    private byte[] zipFiles(List<FileCloneService.ParsedFile> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (FileCloneService.ParsedFile file : files) {
                zos.putNextEntry(new ZipEntry(file.relativePath()));
                zos.write(file.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
