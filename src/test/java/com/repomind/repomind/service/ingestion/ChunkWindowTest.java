package com.repomind.repomind.service.ingestion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkWindowTest {
    @ParameterizedTest @ValueSource(ints={0,1,99,100,101,170,171,185,186,270,271,1000})
    void everyWindowAddsSourceAndHasAnAccurateHeader(int count){
        String body=IntStream.rangeClosed(1,count).mapToObj(i->"line-"+i).collect(Collectors.joining("\r\n"));
        var chunks=new ChunkingService().chunkFile("src/a.java",body);
        if(count==0){assertTrue(chunks.isEmpty());return;}
        int lastCovered=0;
        for(var chunk:chunks){
            assertTrue(chunk.endLine()>lastCovered,"redundant overlap-only window");
            assertEquals(lastCovered==0 ? 1 : lastCovered-14,chunk.startLine());
            assertTrue(chunk.endLine()-chunk.startLine()<100);
            String expected=IntStream.rangeClosed(chunk.startLine(),chunk.endLine()).mapToObj(i->"line-"+i).collect(Collectors.joining("\n"));
            assertEquals("File: src/a.java (lines "+chunk.startLine()+"-"+chunk.endLine()+")\n\n"+expected,chunk.content());
            lastCovered=chunk.endLine();
        }
        assertEquals(count,lastCovered);
    }
}

