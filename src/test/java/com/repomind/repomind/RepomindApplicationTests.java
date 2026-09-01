package com.repomind.repomind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "aws.accessKeyId=test-access-key",
        "aws.secretKey=test-secret-key",
        "aws.s3.bucket=repomind-test-archives",
        "aws.ses.from-email=test@example.com",
        "aws.sqs.ingestion-queue-url=https://sqs.ap-south-1.amazonaws.com/123456789012/repomind-test",
        "aws.sqs.consumer.enabled=false"
})
class RepomindApplicationTests {

	@Test
	void contextLoads() {
	}

}
