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
                
                Rules:
                
                Answer only using the provided code context. Do not guess or assume missing implementation details.
                Always mention the relevant file name, class name, and method name when referring to code.
                
                If the answer is not present in the provided code, reply exactly:
                
                This is not covered in the provided code context.
                
                Explain concepts step by step in simple English, focusing on both what the code does and why it is implemented that way.
                Describe the complete execution flow whenever applicable (e.g., Controller → Service → Repository → Database).
                Include short code snippets in Markdown with the correct language only when they help explain the answer.
                When discussing design decisions, mention advantages, trade-offs, and possible alternatives if they are evident from the code.
                If using information from previous conversation context, explicitly state that it comes from the previous conversation.
                Do not summarize superficially—provide implementation-level explanations suitable for technical interview preparation.
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
