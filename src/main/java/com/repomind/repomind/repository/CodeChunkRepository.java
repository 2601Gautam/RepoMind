package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    long countByRepositoryId(UUID repoId);

    // NOTE: these are bulk JPQL deletes (@Modifying), not derived delete
    // methods. A derived `deleteByX(...)` first SELECTs the matching
    // entities into memory before removing them, and that SELECT hydrates
    // every column — including `embedding`, a pgvector `vector` column.
    // Hibernate/the postgres JDBC driver can't read that type back as a
    // float[] and throws "No results were returned by the query." A bulk
    // @Modifying delete issues one DELETE statement directly and never
    // loads the entity, so it never touches that column.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CodeChunk c WHERE c.repository.id = :repoId")
    void deleteByRepositoryId(@Param("repoId") UUID repoId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CodeChunk c WHERE c.repository.id = :repoId AND c.filePath = :filePath")
    void deleteByRepositoryIdAndFilePath(@Param("repoId") UUID repoId, @Param("filePath") String filePath);

    // Used by SyncService to delete old chunks by captured id after
    // inserting their replacement — also a bulk delete for the same reason.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CodeChunk c WHERE c.id IN :ids")
    void deleteByIdIn(@Param("ids") List<UUID> ids);

    // Id-only projection — never selects `embedding`, so this one was
    // always safe as-is.
    @Query("SELECT c.id FROM CodeChunk c WHERE c.repository.id = :repoId AND c.filePath = :filePath")
    List<UUID> findIdsByRepositoryIdAndFilePath(@Param("repoId") UUID repoId, @Param("filePath") String filePath);

    /**
     * Projection for search results to avoid mapping pgvector's 'vector' type
     * which Hibernate/JDBC has trouble reading as a standard float[].
     */

    interface CodeChunkProjection {
        UUID getId();
        String getFilePath();
        String getContent();
        String getLanguage();
        Integer getStartLine();
        Integer getEndLine();
    }

    @Query(value = """
        SELECT id, file_path as filePath, content, language, start_line as startLine, end_line as endLine
        FROM code_chunks
        WHERE repo_id = :repoId
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<CodeChunkProjection> findTopSimilarChunks(
            @Param("repoId") java.util.UUID repoId,
            @Param("embedding") String embedding,
            @Param("limit") int limit
    );

}