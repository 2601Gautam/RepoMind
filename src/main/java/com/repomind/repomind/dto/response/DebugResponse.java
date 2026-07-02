package com.repomind.repomind.dto.response;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DebugResponse {
    //one line summary: "NullPointerException in AuthService.java line 47"
    private String rootCause;

    //Plain English explanation of why this error happens
    private String explanation;

    //Concrete fix: What to change and where
    private String suggestedFix;

    //how to prevent this class of bug in future
    private String preventionTip;

    //which files from repo were relevent to this error
    //Empty list if no repo was provided
    private List<String> relevantFiles;
}
