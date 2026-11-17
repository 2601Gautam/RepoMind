package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.model.entity.CodeChunk;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.RepoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class IndexSnapshotWriter {
    private final RepoJpaRepository repos;
    private final CodeChunkRepository chunks;

    @Transactional
    public void publishChanges(UUID id, String base, String next, Set<String> affected, List<CodeChunk> replacements) {
        RepoEntity repo = repos.lockForIndexUpdate(id).orElseThrow();
        if (!repo.isSyncing() || !Objects.equals(base,repo.getLastCommitSha()))
            throw new IllegalStateException("Repository revision changed before publication");
        if (!affected.isEmpty()) chunks.deleteChangedPaths(id,affected);
        chunks.saveAllAndFlush(replacements);
        repo.setTotalChunks(Math.toIntExact(chunks.countByRepositoryId(id)));
        repo.setLastCommitSha(next);
        repo.setLastSyncedAt(LocalDateTime.now());
        repo.setErrorMessage(null);
        repo.setSyncMessage(null);
    }
}

