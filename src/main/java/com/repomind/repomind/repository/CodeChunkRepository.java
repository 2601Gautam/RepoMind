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
        SELECT * FROM code_chunks
        WHERE repo_id = CAST(:repoId AS uuid)
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<CodeChunk> findTopSimilarChunks(
            @Param("repoId") String repoId,
            @Param("embedding") String embedding,
            @Param("limit") int limit
    );

}

