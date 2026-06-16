package com.repomind.repomind.utility;

import org.springframework.stereotype.Component;

@Component
public class FileCloneUtil {

    public String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }
}
