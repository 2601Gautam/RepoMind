package com.repomind.repomind.service;

import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.CodeChunkRepository.CodeChunkProjection;
import java.util.*;
import java.util.regex.Pattern;

/** Preserve vector ranking for prose. Code-shaped identifiers contribute a second,
 * repository-scoped ranking without increasing the answer's context window. */
public final class ChatRetrieval {
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private ChatRetrieval() {}

    public static List<CodeChunkProjection> search(CodeChunkRepository chunks, UUID repo, String vector, String question, int limit) {
        var identifiers = new LinkedHashSet<String>();
        var words = WORD.matcher(question);
        while (words.find() && identifiers.size() < 5) {
            String word = words.group();
            if (word.length() < 3 || word.length() > 80) continue;
            if (word.contains("_") || (word.chars().anyMatch(Character::isLowerCase)
                    && word.chars().skip(1).anyMatch(Character::isUpperCase))) {
                identifiers.add(word.toLowerCase(Locale.ROOT));
            }
        }
        if (identifiers.isEmpty()) return chunks.findTopSimilarChunks(repo,vector,limit);
        return chunks.findHybridChunks(repo,vector,String.join(" | ",identifiers),limit,limit*3);
    }
}

