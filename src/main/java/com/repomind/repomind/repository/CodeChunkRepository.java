package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    long countByRepositoryId(UUID repoId);

    void deleteByRepositoryId(UUID repoId);

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

    // Spring cannot auto-generate this query because <=> is pgvector-specific
    // nativeQuery = true means: send this SQL directly to NeonDB as-is,
    // do not translate it through JPA's query language (JPQL)
    //
    // What this query does step by step:
    // 1. Filter to only chunks belonging to this repo (WHERE repo_id = ...)
    // 2. For each remaining chunk, calculate cosine distance between
    //    its embedding and the question's embedding using <=>
    // 3. Sort by that distance ascending (smallest distance = most similar = first)
    // 4. Return only the top :limit results
    //
    // CAST(:embedding AS vector) converts the String "[0.1,-0.2,...]"
    // into PostgreSQL's vector type so the <=> comparison works
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
