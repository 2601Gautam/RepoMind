package com.repomind.repomind.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSourcesTest {
    @Test void redirectsAndMissingEvidenceHaveNoSources() {
        var candidates = List.of("src/Product.java", "README.md", "web/Product.ts");
        for (String answer : List.of("I'm here to help with questions about this codebase—its architecture, setup, testing, debugging, or improvements.",
                "This is not covered in the provided code context.", "Thanks for the kind words!")) {
            assertEquals(List.of(), ChatSources.citedPaths(answer, candidates));
        }
    }
    @Test void onlyReferencedFilesAreSentAndSaved() {
        assertEquals(List.of("src/Product.java", "README.md"), ChatSources.citedPaths("`Product.java` defines the entity. See **README.md**.",
                List.of("src/Product.java", "README.md", "Product.ts")));
        assertEquals(List.of(), ChatSources.citedPaths("OtherProduct.java is unrelated.", List.of("Product.java")));
        assertEquals(List.of("a/index.ts"), ChatSources.citedPaths("`a/index.ts` handles this.", List.of("a/index.ts", "b/index.ts")));
        assertEquals(List.of(), ChatSources.citedPaths("See index.ts", List.of("a/index.ts", "b/index.ts")));
    }
}
