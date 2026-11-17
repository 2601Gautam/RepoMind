package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.PromptBuilder;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in generator evaluation. The reference facts never enter the model messages. */
class LocalGenerationIT {
    /** Fixed key order: Map.of iterates in a per-JVM random order, which would change the JSON and its hash between runs. */
    private static Map<String,String> msg(String role, String content) {
        var m = new LinkedHashMap<String,String>(); m.put("role", role); m.put("content", content); return m;
    }
    @Test void generateFromGoldContext() throws Exception {
        var json = new ObjectMapper();
        var prompts = new PromptBuilder();
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var results = new ArrayList<Map<String,Object>>();
        var cases = json.readTree(Path.of("eval/golden/generation.json").toFile());
        for (JsonNode c : cases) {
            String id=c.get("id").asText(), feature=c.get("feature").asText();
            String evidence=c.get("evidence").asText(), input=c.get("input").asText();
            var messages = new ArrayList<Map<String,String>>();
            String user;
            if (feature.equals("chat")) {
                messages.add(msg("system",prompts.buildChatSystemPrompt()));
                user=prompts.buildChatUserPrompt("",evidence,input);
            } else if (feature.equals("debug")) {
                user=prompts.buildDebugPrompt(input,evidence,"");
            } else {
                user=prompts.buildInterviewPrompt(evidence,"Example",input);
            }
            messages.add(msg("user",user));
            int cap=feature.equals("interview") ? 640 : 200;
            var body=Map.of("messages",messages,"model","local","seed",17,"temperature",0,"max_tokens",cap);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:58093/v1/chat/completions"))
                    .timeout(Duration.ofMinutes(3)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),response.body());
            var payload=json.readTree(response.body());
            var choice=payload.get("choices").get(0);
            results.add(Map.of("id",id,"answer",choice.path("message").path("content").asText(),
                    "stop",choice.path("finish_reason").asText(),"tokens",payload.path("usage"),
                    "promptSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                      .digest(json.writeValueAsBytes(messages)))));
            json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/local-generation.json").toFile(),results);
        }
        assertEquals(24,results.size());
    }
}

