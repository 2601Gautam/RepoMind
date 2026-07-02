package com.repomind.repomind.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class InterviewQuestionDto {
    private UUID id;
    private String question;
    private String expectedAnswer;
    private String difficulty;
    private String conceptTested;
    private Integer questionOrder;
}