package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.PromptBuilder;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Needs no model server. Rebuilds the 24 generation prompts exactly as LocalGenerationIT does and compares each
 * hash with the one saved beside the reviewed answer. A mismatch means the saved 6/24 review no longer describes
 * the current prompts and the local generation run must be repeated.
 */
class PromptDriftTest {
    /** Fixed key order: Map.of iterates in a per-JVM random order, which would change the JSON and its hash between runs. */
    private static Map<String,String> msg(String role, String content) {
        var m = new LinkedHashMap<String,String>(); m.put("role", role); m.put("content", content); return m;
    }
    @Test void currentPromptsMatchTheReviewedGenerationRun() throws Exception {
        var json = new ObjectMapper();
        var prompts = new PromptBuilder();
        var cases = json.readTree(Path.of("eval/golden/generation.json").toFile());
        var saved = new HashMap<String, String>();
        for (JsonNode r : json.readTree(Path.of("eval/results/generation-after.json").toFile()))
            saved.put(r.get("id").asText(), r.get("promptSha256").asText());
        var drifted = new ArrayList<String>();
        int checked = 0;
        for (JsonNode c : cases) {
            String id = c.get("id").asText(), feature = c.get("feature").asText();
            String evidence = c.get("evidence").asText(), input = c.get("input").asText();
            var messages = new ArrayList<Map<String, String>>();
            String user;
            if (feature.equals("chat")) {
                messages.add(msg("system", prompts.buildChatSystemPrompt()));
                user = prompts.buildChatUserPrompt("", evidence, input);
            } else if (feature.equals("debug")) {
                user = prompts.buildDebugPrompt(input, evidence, "");
            } else {
                user = prompts.buildInterviewPrompt(evidence, "Example", input);
            }
            messages.add(msg("user", user));
            String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(messages)));
            checked++;
            if (!hash.equals(saved.get(id))) drifted.add(id);
        }
        assertFalse(saved.isEmpty());
        assertEquals(24, checked);
        assertEquals(List.of(), drifted, "prompt text changed after the reviewed generation run for these cases");
    }
}
