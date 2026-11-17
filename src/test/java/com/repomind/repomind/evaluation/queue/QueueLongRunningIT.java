package com.repomind.repomind.evaluation.queue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.service.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"ingestion.embedding-batch-size=1","aws.sqs.ingestion-lease-seconds=2"})
@ActiveProfiles("eval") @Import(QueueFixture.class)
class QueueLongRunningIT {
 @Autowired IngestionService service;
 @Autowired JdbcTemplate db;
 @Autowired ObjectMapper json;
 @Autowired QueueFixture.Work work;
 @Test void longCloneOutlivesLeaseAndVisibilityWithoutStartingAgain() throws Exception {
  UUID id=UUID.randomUUID();
  var entered=new CountDownLatch(1);
  var release=new CountDownLatch(1);
  var clones=new AtomicInteger();
  work.beforeClone=()->{
   clones.incrementAndGet(); entered.countDown();
   try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
  };
  try(var sqs=QueueFixture.client()){
   String queue=sqs.createQueue(b->b.queueName("long-"+id)).queueUrl();
   var first=QueueFixture.consumer(sqs,json,queue,service,1,1,0);
   var second=QueueFixture.consumer(sqs,json,queue,service,1,1,0);
   try {
    db.update("insert into repositories(id,github_url,status) values(?,?,'PENDING')",id,QueueFixture.url(id));
    var publisher=QueueFixture.publisher(sqs,json,queue);
    publisher.enqueue(id,QueueFixture.url(id),null);
    first.pollQueue(); assertTrue(entered.await(5,TimeUnit.SECONDS));
    Thread.sleep(3100);
    assertTrue(sqs.receiveMessage(b->b.queueUrl(queue).waitTimeSeconds(0)).messages().isEmpty(),
            "The active worker must keep its original receipt invisible");
    // Also force an independent duplicate, even if the original receipt remains hidden.
    publisher.enqueue(id,QueueFixture.url(id),null);
    second.pollQueue(); Thread.sleep(500);
    int starts=clones.get();
    release.countDown(); first.shutdown(); second.shutdown();
    assertEquals(1,starts,"Only the live lease owner may enter clone");
    assertEquals("READY",db.queryForObject("select status from repositories where id=?",String.class,id));
    assertEquals(2,db.queryForObject("select count(*) from code_chunks where repo_id=?",Integer.class,id));
   } finally {
    release.countDown(); first.shutdown();second.shutdown();work.beforeClone=()->{};
    sqs.deleteQueue(b->b.queueUrl(queue));db.update("delete from repositories where id=?",id);
   }
  }
 }
}
