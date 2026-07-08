package com.repomind.repomind.service;

import org.springframework.stereotype.Component;

import java.util.List;

// Follows Single Responsibility — builds prompts, nothing else
// Follows Open/Closed — add new prompt types by adding new methods,
// not modifying existing ones
@Component
public class PromptBuilder {
    // Sliding window size — how many recent message PAIRS to include
    // 8 pairs = 16 messages = enough context without overwhelming the LLM
    public static final int MEMORY_WINDOW_SIZE = 16;

    public String buildChatSystemPrompt(){
        return """
            You are an expert software engineer helping developers understand a codebase.
            
            Rules you must follow:
            1. Answer ONLY based on the code provided in the context below
            2. Always mention specific file names when referring to code
            3. If the answer is not visible in the provided code, say exactly:
               "This is not covered in the provided code context."
            4. Format code examples using markdown code blocks with the language name
            5. Be specific and concise — developers prefer precision over length
            6. When referencing previous conversation context, be explicit about it
            """;
    }

    // Builds the user prompt including:
    // - Conversation history (for memory/context)
    // - Retrieved code chunks (for RAG)
    // - The current question
    public String buildChatUserPrompt(
        List<ConversationMemoryService.MemoryMessage> history,
        String codeContext,
        String currentQuestion){
        StringBuilder prompt = new StringBuilder();

        // Include conversation history if this is not the first message
        // History gives the LLM context about what was already discussed
        if(!history.isEmpty()){
            prompt.append("PREVIOUS CONVERSATION:\n");
            history.forEach(msg ->
                    prompt.append(msg.role().equals("user") ? "Developer: " : "Assistant: ")
                            .append(msg.content())
                            .append("\n\n"));
            prompt.append("-------\n\n");
        }

        prompt.append("CODE CONTEXT FROM REPOSITORY:\n\n")
                .append(codeContext)
                .append("\n\n")
                .append("CURRENT QUESTION: ")
                .append(currentQuestion)
                .append("\n\nAnswer based on the code and conversation context above.");

        return prompt.toString();
    }

}
