package com.repomind.repomind.service;

import java.util.List;
import java.util.regex.Pattern;

final class ChatSources {
    private ChatSources() {}

    static List<String> citedPaths(String answer, List<String> sources) {
        if (answer == null || answer.isBlank()) return List.of();
        String text = answer.replace('\\', '/');
        var paths = sources.stream().distinct().toList();
        return paths.stream().filter(path -> {
            String normalized = path.replace('\\', '/');
            String filename = basename(path);
            return mentions(text, normalized) ||
                    (paths.stream().filter(other -> basename(other).equals(filename)).count() == 1 && mentions(text, filename));
        }).toList();
    }

    private static String basename(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static boolean mentions(String text, String path) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_.-])" + Pattern.quote(path) + "(?![\\p{L}\\p{N}_-])")
                .matcher(text).find();
    }
}
